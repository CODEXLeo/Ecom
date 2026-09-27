package com.microservice.payments.payment.service;

import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.microservice.payments.config.properties.PaymentProperties;
import com.microservice.payments.exception.IdempotencyReplayException;
import com.microservice.payments.exception.InvalidPaymentStateException;
import com.microservice.payments.exception.KnownPaymentProviderFailureException;
import com.microservice.payments.exception.PaymentConflictException;
import com.microservice.payments.exception.PaymentNotFoundException;
import com.microservice.payments.exception.PaymentProviderException;
import com.microservice.payments.idempotency.service.IdempotencyResult;
import com.microservice.payments.idempotency.service.IdempotencyService;
import com.microservice.payments.idempotency.service.RequestHashService;
import com.microservice.payments.payment.dto.request.AuthorizePaymentRequest;
import com.microservice.payments.payment.dto.response.PaymentResponse;
import com.microservice.payments.payment.entity.Payment;
import com.microservice.payments.payment.entity.PaymentStatus;
import com.microservice.payments.payment.mapper.PaymentMapper;
import com.microservice.payments.payment.repository.PaymentRepository;
import com.microservice.payments.payment.state.PaymentStateMachine;
import com.microservice.payments.provider.PaymentProvider;
import com.microservice.payments.provider.dto.ProviderAuthorizationRequest;
import com.microservice.payments.provider.dto.ProviderAuthorizationResponse;
import com.microservice.payments.provider.dto.ProviderOperationResponse;
import com.microservice.payments.security.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentMapper paymentMapper;

    private final PaymentProvider paymentProvider;

    private final PaymentStateMachine stateMachine;

    private final IdempotencyService idempotencyService;

    private final RequestHashService requestHashService;

    private final ObjectMapper objectMapper;

    private final PaymentProperties paymentProperties;

    private final CurrentUser currentUser;

    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper,
            PaymentProvider paymentProvider,
            PaymentStateMachine stateMachine,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            ObjectMapper objectMapper,
            PaymentProperties paymentProperties,
            CurrentUser currentUser,
            PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository =
                paymentRepository;

        this.paymentMapper =
                paymentMapper;

        this.paymentProvider =
                paymentProvider;

        this.stateMachine =
                stateMachine;

        this.idempotencyService =
                idempotencyService;

        this.requestHashService =
                requestHashService;

        this.objectMapper =
                objectMapper;

        this.paymentProperties =
                paymentProperties;

        this.currentUser =
                currentUser;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );
    }

    // ================================================================
    // AUTHORIZE
    // ================================================================

    @Override
    public PaymentResponse authorize(
            AuthorizePaymentRequest request,
            String idempotencyKey
    ) {

        validateRequest(
                request
        );

        String normalizedKey =
                validateIdempotencyKey(
                        idempotencyKey
                );

        String requestHash =
                requestHashService.hash(
                        request
                );

        /*
         * Establish application-level idempotency before doing any
         * payment work.
         */
        IdempotencyResult result =
                idempotencyService.begin(
                        request.userId(),
                        normalizedKey,
                        requestHash
                );

        if (!result.shouldProcess()) {

            if (result.isFailedReplay()) {
                throw new IdempotencyReplayException(
                        result.responseStatus(),
                        result.responseBody()
                );
            }

            return deserializeResponse(
                    result.responseBody()
            );
        }

        UUID paymentId =
                deterministicPaymentId(
                        request.userId(),
                        normalizedKey
                );

        /*
         * Create/recover the local payment.
         */
        PaymentSnapshot existing =
                loadPaymentSnapshot(
                        paymentId
                );

        if (existing != null) {

            if (!existing.orderId().equals(
                    request.orderId()
            )) {
                throw new PaymentConflictException(
                        "Payment ID is already associated "
                                + "with another order"
                );
            }

            if (!existing.userId().equals(
                    request.userId()
            )) {
                throw new PaymentConflictException(
                        "Payment belongs to another user"
                );
            }

            if (existing.status()
                    != PaymentStatus.PENDING) {

                PaymentResponse response =
                        existing.response();

                safelyCompleteIdempotency(
                        request.userId(),
                        normalizedKey,
                        requestHash,
                        response
                );

                return response;
            }

        } else {

            PaymentSnapshot paymentForOrder =
                    loadPaymentByOrder(
                            request.orderId()
                    );

            if (paymentForOrder != null) {

                if (!paymentForOrder.userId().equals(
                        request.userId()
                )) {
                    throw new PaymentConflictException(
                            "Order already has a payment "
                                    + "owned by another user"
                    );
                }

                throw new PaymentConflictException(
                        "Order already has a payment: "
                                + paymentForOrder.paymentId()
                );
            }

            createPendingPayment(
                    paymentId,
                    request
            );
        }

        /*
         * Provider is deliberately outside the database transaction.
         *
         * This prevents a remote/provider operation from holding
         * a local database transaction open.
         */
        ProviderAuthorizationResponse providerResponse =
                paymentProvider.authorize(
                        new ProviderAuthorizationRequest(
                                paymentId,
                                request.orderId(),
                                request.amount(),
                                request.currency(),
                                request.paymentMethod()
                        ),
                        providerOperationKey(
                                paymentId,
                                "authorize",
                                normalizedKey
                        )
                );

        PaymentResponse response;

        /*
         * ------------------------------------------------------------
         * PROVIDER REJECTION
         * ------------------------------------------------------------
         */
        if (!providerResponse.successful()) {

            response =
                    transactionTemplate.execute(
                            status -> {

                                Payment payment =
                                        paymentRepository
                                                .findByIdForUpdate(
                                                        paymentId
                                                )
                                                .orElseThrow(
                                                        () ->
                                                                new PaymentNotFoundException(
                                                                        "Payment not found: "
                                                                                + paymentId
                                                                )
                                                );

                                if (payment.getStatus()
                                        == PaymentStatus.FAILED) {

                                    return paymentMapper
                                            .toResponse(
                                                    payment
                                            );
                                }

                                stateMachine.validateTransition(
                                        payment.getStatus(),
                                        PaymentStatus.FAILED
                                );

                                payment.fail(
                                        providerResponse.failureCode(),
                                        providerResponse.failureMessage()
                                );

                                paymentRepository.flush();

                                return paymentMapper.toResponse(
                                        payment
                                );
                            }
                    );

            /*
             * A known provider rejection is a durable terminal
             * result. Store it so the same idempotency key can replay.
             */
            safelyCompleteIdempotency(
                    request.userId(),
                    normalizedKey,
                    requestHash,
                    response
            );

            return response;
        }

        /*
         * ------------------------------------------------------------
         * PROVIDER SUCCESS
         * ------------------------------------------------------------
         */
        if (providerResponse.providerPaymentId() == null
                || providerResponse.providerPaymentId().isBlank()) {

            /*
             * Provider success without a provider payment ID is
             * ambiguous. Do not mark the local payment AUTHORIZED.
             */
            throw new PaymentProviderException(
                    "Provider authorization succeeded "
                            + "without a provider payment ID"
            );
        }

        response =
                transactionTemplate.execute(
                        status -> {

                            Payment payment =
                                    paymentRepository
                                            .findByIdForUpdate(
                                                    paymentId
                                            )
                                            .orElseThrow(
                                                    () ->
                                                            new PaymentNotFoundException(
                                                                    "Payment not found: "
                                                                            + paymentId
                                                            )
                                            );

                            if (payment.getStatus()
                                    == PaymentStatus.AUTHORIZED) {

                                return paymentMapper
                                        .toResponse(
                                                payment
                                        );
                            }

                            stateMachine.validateTransition(
                                    payment.getStatus(),
                                    PaymentStatus.AUTHORIZED
                            );

                            payment.authorize(
                                    providerResponse
                                            .providerPaymentId()
                            );

                            paymentRepository.flush();

                            return paymentMapper.toResponse(
                                    payment
                            );
                        }
                );

        /*
         * Complete application-level idempotency only after the
         * local payment has definitely reached AUTHORIZED.
         */
        safelyCompleteIdempotency(
                request.userId(),
                normalizedKey,
                requestHash,
                response
        );

        return response;
    }

    // ================================================================
    // CUSTOMER LOOKUP
    // ================================================================

    @Override
    public PaymentResponse getPayment(
            UUID paymentId
    ) {

        validatePaymentId(
                paymentId
        );

        UUID userId =
                currentUser.userId();

        return transactionTemplate.execute(
                status ->
                        paymentRepository
                                .findByPaymentIdAndUserId(
                                        paymentId,
                                        userId
                                )
                                .map(
                                        paymentMapper::toResponse
                                )
                                .orElseThrow(
                                        () ->
                                                new PaymentNotFoundException(
                                                        "Payment not found: "
                                                                + paymentId
                                                )
                                )
        );
    }

    // ================================================================
    // CAPTURE
    // ================================================================

    @Override
    public PaymentResponse capture(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                idempotencyKey,
                "capture"
        );
    }

    // ================================================================
    // VOID
    // ================================================================

    @Override
    public PaymentResponse voidPayment(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                idempotencyKey,
                "void"
        );
    }

    // ================================================================
    // REFUND
    // ================================================================

    @Override
    public PaymentResponse refund(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                idempotencyKey,
                "refund"
        );
    }

    // ================================================================
    // GENERIC STATE-CHANGING OPERATION
    // ================================================================

    private PaymentResponse executeOperation(
            UUID paymentId,
            String idempotencyKey,
            String operation
    ) {

        validatePaymentId(
                paymentId
        );

        String normalizedKey =
                validateIdempotencyKey(
                        idempotencyKey
                );

        UUID userId =
                loadPaymentUserId(
                        paymentId
                );

        String requestHash =
                requestHashService.hash(
                        new PaymentOperationRequest(
                                paymentId,
                                operation
                        )
                );

        IdempotencyResult result =
                idempotencyService.begin(
                        userId,
                        normalizedKey,
                        requestHash
                );

        if (!result.shouldProcess()) {

            if (result.isFailedReplay()) {
                throw new IdempotencyReplayException(
                        result.responseStatus(),
                        result.responseBody()
                );
            }

            return deserializeResponse(
                    result.responseBody()
            );
        }

        PaymentOperationContext context =
                loadOperationContext(
                        paymentId
                );

        ProviderOperationResponse providerResponse;

        switch (operation) {

            case "capture" ->
                    providerResponse =
                            paymentProvider.capture(
                                    context.providerPaymentId(),
                                    providerOperationKey(
                                            paymentId,
                                            "capture",
                                            normalizedKey
                                    )
                            );

            case "void" ->
                    providerResponse =
                            paymentProvider.voidPayment(
                                    context.providerPaymentId(),
                                    providerOperationKey(
                                            paymentId,
                                            "void",
                                            normalizedKey
                                    )
                            );

            case "refund" ->
                    providerResponse =
                            paymentProvider.refund(
                                    context.providerPaymentId(),
                                    providerOperationKey(
                                            paymentId,
                                            "refund",
                                            normalizedKey
                                    )
                            );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported payment operation: "
                                    + operation
                    );
        }

        if (!providerResponse.successful()) {

            KnownPaymentProviderFailureException failure =
                    providerOperationFailure(
                            operation,
                            providerResponse
                    );

            /*
             * Provider gave a definitive failure. Persist the
             * 502 response so the same idempotency key can replay it.
             */
            safelyCompleteOperationFailure(
                    userId,
                    normalizedKey,
                    requestHash,
                    502,
                    failure.getMessage()
            );

            throw failure;
        }

        PaymentResponse response =
                transactionTemplate.execute(
                        status -> {

                            Payment payment =
                                    paymentRepository
                                            .findByIdForUpdate(
                                                    paymentId
                                            )
                                            .orElseThrow(
                                                    () ->
                                                            new PaymentNotFoundException(
                                                                    "Payment not found: "
                                                                            + paymentId
                                                            )
                                            );

                            switch (operation) {

                                case "capture" ->
                                        payment.capture();

                                case "void" ->
                                        payment.voidPayment();

                                case "refund" ->
                                        payment.refund();

                                default ->
                                        throw new IllegalArgumentException(
                                                "Unsupported payment operation: "
                                                        + operation
                                        );
                            }

                            paymentRepository.flush();

                            return paymentMapper.toResponse(
                                    payment
                            );
                        }
                );

        safelyCompleteOperation(
                userId,
                normalizedKey,
                requestHash,
                response
        );

        return response;
    }

    // ================================================================
    // CREATE PENDING PAYMENT
    // ================================================================

    private void createPendingPayment(
            UUID paymentId,
            AuthorizePaymentRequest request
    ) {

        transactionTemplate.executeWithoutResult(
                status -> {

                    Payment existingForOrder =
                            paymentRepository
                                    .findByOrderIdForUpdate(
                                            request.orderId()
                                    )
                                    .orElse(null);

                    if (existingForOrder != null) {

                        if (!existingForOrder.getPaymentId()
                                .equals(paymentId)) {

                            throw new PaymentConflictException(
                                    "Order already has payment "
                                            + existingForOrder.getPaymentId()
                            );
                        }

                        return;
                    }

                    Payment payment =
                            Payment.create(
                                    paymentId,
                                    request.orderId(),
                                    request.userId(),
                                    request.paymentMethod(),
                                    request.amount(),
                                    request.currency()
                            );

                    paymentRepository.saveAndFlush(
                            payment
                    );
                }
        );
    }

    // ================================================================
    // PAYMENT LOOKUPS
    // ================================================================

    private UUID loadPaymentUserId(
            UUID paymentId
    ) {

        return transactionTemplate.execute(
                status ->
                        paymentRepository
                                .findById(
                                        paymentId
                                )
                                .map(
                                        Payment::getUserId
                                )
                                .orElseThrow(
                                        () ->
                                                new PaymentNotFoundException(
                                                        "Payment not found: "
                                                                + paymentId
                                                )
                                )
        );
    }

    private PaymentSnapshot loadPaymentSnapshot(
            UUID paymentId
    ) {

        return transactionTemplate.execute(
                status ->
                        paymentRepository
                                .findById(
                                        paymentId
                                )
                                .map(
                                        payment ->
                                                new PaymentSnapshot(
                                                        payment.getPaymentId(),
                                                        payment.getOrderId(),
                                                        payment.getUserId(),
                                                        payment.getStatus(),
                                                        paymentMapper.toResponse(
                                                                payment
                                                        )
                                                )
                                )
                                .orElse(null)
        );
    }

    private PaymentSnapshot loadPaymentByOrder(
            UUID orderId
    ) {

        return transactionTemplate.execute(
                status ->
                        paymentRepository
                                .findByOrderIdForUpdate(
                                        orderId
                                )
                                .map(
                                        payment ->
                                                new PaymentSnapshot(
                                                        payment.getPaymentId(),
                                                        payment.getOrderId(),
                                                        payment.getUserId(),
                                                        payment.getStatus(),
                                                        paymentMapper.toResponse(
                                                                payment
                                                        )
                                                )
                                )
                                .orElse(null)
        );
    }

    private PaymentOperationContext loadOperationContext(
            UUID paymentId
    ) {

        return transactionTemplate.execute(
                status ->
                        paymentRepository
                                .findByIdForUpdate(
                                        paymentId
                                )
                                .map(
                                        payment -> {

                                            if (payment.getProviderPaymentId()
                                                    == null
                                                    || payment.getProviderPaymentId()
                                                            .isBlank()) {

                                                throw new InvalidPaymentStateException(
                                                        "Payment has no provider payment ID"
                                                );
                                            }

                                            /*
                                             * The provider is the source of truth
                                             * for the external operation.
                                             *
                                             * Do not reject capture/void/refund
                                             * merely because local state is already
                                             * terminal. The provider must be allowed
                                             * to return its definitive result.
                                             */
                                            return new PaymentOperationContext(
                                                    payment.getStatus(),
                                                    payment.getProviderPaymentId(),
                                                    paymentMapper.toResponse(
                                                            payment
                                                    )
                                            );
                                        }
                                )
                                .orElseThrow(
                                        () ->
                                                new PaymentNotFoundException(
                                                        "Payment not found: "
                                                                + paymentId
                                                )
                                )
        );
    }

    // ================================================================
    // IDEMPOTENCY
    // ================================================================

    private void safelyCompleteIdempotency(
            UUID userId,
            String key,
            String hash,
            PaymentResponse response
    ) {

        try {

            idempotencyService.complete(
                    userId,
                    key,
                    hash,
                    201,
                    objectMapper.writeValueAsString(
                            response
                    )
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to serialize payment response",
                    exception
            );
        }
    }

    private void safelyCompleteOperation(
            UUID userId,
            String key,
            String hash,
            PaymentResponse response
    ) {

        try {

            idempotencyService.complete(
                    userId,
                    key,
                    hash,
                    200,
                    objectMapper.writeValueAsString(
                            response
                    )
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to serialize payment response",
                    exception
            );
        }
    }

    private void safelyCompleteOperationFailure(
            UUID userId,
            String key,
            String hash,
            int status,
            String message
    ) {

        try {

            String body =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "title",
                                    "Payment provider unavailable",
                                    "status",
                                    status,
                                    "detail",
                                    "The payment provider could not complete "
                                            + "the requested operation."
                            )
                    );

            idempotencyService.completeFailure(
                    userId,
                    key,
                    hash,
                    status,
                    body
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to serialize provider failure response",
                    exception
            );
        }
    }

    // ================================================================
    // PROVIDER FAILURE
    // ================================================================

    private KnownPaymentProviderFailureException
    providerOperationFailure(
            String operation,
            ProviderOperationResponse providerResponse
    ) {

        StringBuilder message =
                new StringBuilder(
                        "Payment provider "
                                + operation
                                + " failed"
                );

        if (providerResponse.failureCode() != null
                && !providerResponse.failureCode().isBlank()) {

            message.append(" [")
                    .append(
                            providerResponse.failureCode()
                    )
                    .append("]");
        }

        if (providerResponse.failureMessage() != null
                && !providerResponse.failureMessage().isBlank()) {

            message.append(": ")
                    .append(
                            providerResponse.failureMessage()
                    );
        }

        return new KnownPaymentProviderFailureException(
                message.toString()
        );
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    private void validateRequest(
            AuthorizePaymentRequest request
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Payment request must not be null"
            );
        }

        if (request.orderId() == null) {

            throw new IllegalArgumentException(
                    "Order ID is required"
            );
        }

        if (request.userId() == null) {

            throw new IllegalArgumentException(
                    "User ID is required"
            );
        }

        if (request.paymentMethod() == null) {

            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }

        if (request.paymentMethod()
                == com.microservice.payments.payment.entity.PaymentMethod.COD) {

            throw new IllegalArgumentException(
                    "COD must not be sent to Payment Service"
            );
        }

        if (request.amount() == null
                || request.amount().signum() <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (request.currency() == null
                || request.currency().isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }
    }

    private String validateIdempotencyKey(
            String idempotencyKey
    ) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key is required"
            );
        }

        String normalized =
                idempotencyKey.trim();

        if (normalized.length()
                > paymentProperties.idempotencyKeyMaxLength()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key exceeds maximum length"
            );
        }

        return normalized;
    }

    private void validatePaymentId(
            UUID paymentId
    ) {

        if (paymentId == null) {

            throw new IllegalArgumentException(
                    "Payment ID is required"
            );
        }
    }

    // ================================================================
    // DETERMINISTIC IDS
    // ================================================================

    private UUID deterministicPaymentId(
            UUID userId,
            String idempotencyKey
    ) {

        return UUID.nameUUIDFromBytes(
                (
                        "payment:"
                                + userId
                                + ":"
                                + idempotencyKey
                ).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );
    }

    /*
     * Provider idempotency is intentionally based on BOTH:
     *
     *     payment + operation + application idempotency key
     *
     * The application idempotency layer handles retries using the same
     * HTTP Idempotency-Key before reaching the provider.
     *
     * A NEW HTTP Idempotency-Key must therefore produce a NEW provider
     * operation key. Otherwise a previous successful provider operation
     * could be replayed as success and hide a real provider-side state
     * failure such as:
     *
     *     CAPTURED -> capture
     */
    private String providerOperationKey(
            UUID paymentId,
            String operation,
            String idempotencyKey
    ) {

        return "payment:"
                + paymentId
                + ":"
                + operation
                + ":"
                + idempotencyKey;
    }

    // ================================================================
    // RESPONSE DESERIALIZATION
    // ================================================================

    private PaymentResponse deserializeResponse(
            String responseBody
    ) {

        try {

            return objectMapper.readValue(
                    responseBody,
                    PaymentResponse.class
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to deserialize stored payment response",
                    exception
            );
        }
    }

    // ================================================================
    // INTERNAL RECORDS
    // ================================================================

    private record PaymentSnapshot(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            PaymentStatus status,
            PaymentResponse response
    ) {
    }

    private record PaymentOperationContext(
            PaymentStatus status,
            String providerPaymentId,
            PaymentResponse response
    ) {
    }

    private record PaymentOperationRequest(
            UUID paymentId,
            String operation
    ) {
    }
}