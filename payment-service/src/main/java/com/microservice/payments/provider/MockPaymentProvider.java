package com.microservice.payments.provider;

import java.util.UUID;

import com.microservice.payments.exception.PaymentProviderException;
import com.microservice.payments.idempotency.service.RequestHashService;
import com.microservice.payments.payment.entity.PaymentMethod;
import com.microservice.payments.provider.dto.ProviderAuthorizationRequest;
import com.microservice.payments.provider.dto.ProviderAuthorizationResponse;
import com.microservice.payments.provider.dto.ProviderOperationResponse;
import com.microservice.payments.provider.entity.MockProviderOperation;
import com.microservice.payments.provider.entity.MockProviderOperationType;
import com.microservice.payments.provider.entity.MockProviderPayment;
import com.microservice.payments.provider.repository.MockProviderOperationRepository;
import com.microservice.payments.provider.repository.MockProviderPaymentRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MockPaymentProvider
        implements PaymentProvider {

    private final MockProviderPaymentRepository
            paymentRepository;

    private final MockProviderOperationRepository
            operationRepository;

    private final RequestHashService
            requestHashService;

    private final TransactionTemplate
            transactionTemplate;

    public MockPaymentProvider(
            MockProviderPaymentRepository paymentRepository,
            MockProviderOperationRepository operationRepository,
            RequestHashService requestHashService,
            PlatformTransactionManager transactionManager
    ) {

        this.paymentRepository =
                paymentRepository;

        this.operationRepository =
                operationRepository;

        this.requestHashService =
                requestHashService;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );
    }

    // ================================================================
    // AUTHORIZE
    // ================================================================

    @Override
    public ProviderAuthorizationResponse authorize(
            ProviderAuthorizationRequest request,
            String idempotencyKey
    ) {

        validateAuthorizationRequest(
                request,
                idempotencyKey
        );

        String requestHash =
                requestHashService.hash(
                        request
                );

        /*
         * ------------------------------------------------------------
         * PROVIDER-SIDE IDEMPOTENCY LOOKUP
         * ------------------------------------------------------------
         *
         * Same idempotency key + same request:
         *     return the original provider result.
         *
         * Same idempotency key + different request:
         *     reject the request.
         */
        ProviderAuthorizationResponse existing =
                transactionTemplate.execute(
                        status ->
                                findAuthorizationResult(
                                        idempotencyKey,
                                        requestHash
                                )
                );

        if (existing != null) {
            return existing;
        }

        /*
         * ------------------------------------------------------------
         * CREATE PROVIDER AUTHORIZATION
         * ------------------------------------------------------------
         *
         * The entire provider-side operation is one local DB
         * transaction.
         *
         * If two concurrent requests race on the same idempotency
         * key, the database unique constraint decides the winner.
         */
        try {

            return transactionTemplate.execute(
                    status ->
                            createAuthorization(
                                    request,
                                    idempotencyKey,
                                    requestHash
                            )
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * Another concurrent request may have won the
             * idempotency-key race.
             *
             * Read its committed result.
             */
            ProviderAuthorizationResponse result =
                    transactionTemplate.execute(
                            status ->
                                    findAuthorizationResult(
                                            idempotencyKey,
                                            requestHash
                                    )
                    );

            if (result != null) {
                return result;
            }

            throw new PaymentProviderException(
                    "Unable to establish provider authorization state",
                    exception
            );
        }
    }

    private ProviderAuthorizationResponse
    createAuthorization(
            ProviderAuthorizationRequest request,
            String idempotencyKey,
            String requestHash
    ) {

        /*
         * ------------------------------------------------------------
         * PROVIDER INPUT VALIDATION
         * ------------------------------------------------------------
         */

        if (request.amount() == null
                || request.amount().signum() <= 0) {

            String failureCode =
                    "INVALID_AMOUNT";

            String failureMessage =
                    "Payment amount must be greater than zero";

            MockProviderOperation operation =
                    MockProviderOperation.failure(
                            MockProviderOperationType.AUTHORIZE,
                            idempotencyKey,
                            requestHash,
                            null,
                            failureCode,
                            failureMessage
                    );

            operationRepository.saveAndFlush(
                    operation
            );

            return new ProviderAuthorizationResponse(
                    false,
                    null,
                    failureCode,
                    failureMessage
            );
        }

        if (request.paymentId() == null) {

            throw new PaymentProviderException(
                    "Payment ID is required"
            );
        }

        if (request.orderId() == null) {

            throw new PaymentProviderException(
                    "Order ID is required"
            );
        }

        if (request.currency() == null
                || request.currency().isBlank()) {

            throw new PaymentProviderException(
                    "Currency is required"
            );
        }

        if (request.paymentMethod() == null
                || request.paymentMethod()
                        == PaymentMethod.COD) {

            throw new PaymentProviderException(
                    "A non-COD payment method is required"
            );
        }

        /*
         * ------------------------------------------------------------
         * CREATE PROVIDER PAYMENT
         * ------------------------------------------------------------
         *
         * The provider payment ID is generated exactly once.
         */
        String providerPaymentId =
                "mock_"
                        + UUID.randomUUID();

        MockProviderPayment providerPayment =
                MockProviderPayment.authorize(
                        providerPaymentId,
                        request.paymentId(),
                        request.orderId(),
                        request.amount(),
                        request.currency(),
                        request.paymentMethod()
                );

        paymentRepository.save(
                providerPayment
        );

        /*
         * ------------------------------------------------------------
         * PERSIST PROVIDER IDEMPOTENCY RESULT
         * ------------------------------------------------------------
         */
        MockProviderOperation operation =
                MockProviderOperation.success(
                        MockProviderOperationType.AUTHORIZE,
                        idempotencyKey,
                        requestHash,
                        providerPaymentId
                );

        operationRepository.saveAndFlush(
                operation
        );

        return new ProviderAuthorizationResponse(
                true,
                providerPaymentId,
                null,
                null
        );
    }

    private ProviderAuthorizationResponse
    findAuthorizationResult(
            String idempotencyKey,
            String requestHash
    ) {

        return operationRepository
                .findByOperationTypeAndIdempotencyKey(
                        MockProviderOperationType.AUTHORIZE,
                        idempotencyKey
                )
                .map(operation -> {

                    verifyRequestHash(
                            operation,
                            requestHash
                    );

                    return new ProviderAuthorizationResponse(
                            operation.isSuccessful(),
                            operation.getProviderPaymentId(),
                            operation.getFailureCode(),
                            operation.getFailureMessage()
                    );
                })
                .orElse(null);
    }

    // ================================================================
    // CAPTURE
    // ================================================================

    @Override
    public ProviderOperationResponse capture(
            String providerPaymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                MockProviderOperationType.CAPTURE,
                providerPaymentId,
                idempotencyKey
        );
    }

    // ================================================================
    // VOID
    // ================================================================

    @Override
    public ProviderOperationResponse voidPayment(
            String providerPaymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                MockProviderOperationType.VOID,
                providerPaymentId,
                idempotencyKey
        );
    }

    // ================================================================
    // REFUND
    // ================================================================

    @Override
    public ProviderOperationResponse refund(
            String providerPaymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                MockProviderOperationType.REFUND,
                providerPaymentId,
                idempotencyKey
        );
    }

    // ================================================================
    // GENERIC PROVIDER OPERATION
    // ================================================================

    private ProviderOperationResponse executeOperation(
            MockProviderOperationType operationType,
            String providerPaymentId,
            String idempotencyKey
    ) {

        validateOperation(
                providerPaymentId,
                idempotencyKey
        );

        /*
         * The operation request consists of the provider payment ID.
         *
         * The operation type is already part of the provider
         * idempotency identity:
         *
         *     (operation_type, idempotency_key)
         *
         * Therefore it does not need to be duplicated inside the
         * request fingerprint.
         */
        String requestHash =
                requestHashService.hash(
                        new ProviderOperationRequest(
                                providerPaymentId
                        )
                );

        /*
         * ------------------------------------------------------------
         * PROVIDER-SIDE IDEMPOTENCY LOOKUP
         * ------------------------------------------------------------
         */
        ProviderOperationResponse existing =
                transactionTemplate.execute(
                        status ->
                                findOperationResult(
                                        operationType,
                                        idempotencyKey,
                                        requestHash
                                )
                );

        if (existing != null) {
            return existing;
        }

        /*
         * ------------------------------------------------------------
         * EXECUTE PROVIDER OPERATION
         * ------------------------------------------------------------
         */
        try {

            return transactionTemplate.execute(
                    status ->
                            createOperation(
                                    operationType,
                                    providerPaymentId,
                                    idempotencyKey,
                                    requestHash
                            )
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * Another concurrent request may have created the
             * operation first.
             */
            ProviderOperationResponse result =
                    transactionTemplate.execute(
                            status ->
                                    findOperationResult(
                                            operationType,
                                            idempotencyKey,
                                            requestHash
                                    )
                    );

            if (result != null) {
                return result;
            }

            throw new PaymentProviderException(
                    "Unable to establish provider operation state",
                    exception
            );
        }
    }

    private ProviderOperationResponse
    createOperation(
            MockProviderOperationType operationType,
            String providerPaymentId,
            String idempotencyKey,
            String requestHash
    ) {

        /*
         * Lock the provider payment.
         *
         * This serializes state-changing operations such as:
         *
         *     AUTHORIZED -> CAPTURED
         *     AUTHORIZED -> VOIDED
         *     CAPTURED   -> REFUNDED
         */
        MockProviderPayment payment =
                paymentRepository
                        .findByProviderPaymentIdForUpdate(
                                providerPaymentId
                        )
                        .orElseThrow(
                                () ->
                                        new PaymentProviderException(
                                                "Unknown provider payment ID: "
                                                        + providerPaymentId
                                        )
                        );

        try {

            switch (operationType) {

                case CAPTURE ->
                        payment.capture();

                case VOID ->
                        payment.voidPayment();

                case REFUND ->
                        payment.refund();

                default ->
                        throw new IllegalStateException(
                                "Unsupported provider operation: "
                                        + operationType
                        );
            }

            paymentRepository.flush();

            /*
             * Provider operation succeeded.
             */
            MockProviderOperation operation =
                    MockProviderOperation.success(
                            operationType,
                            idempotencyKey,
                            requestHash,
                            providerPaymentId
                    );

            operationRepository.saveAndFlush(
                    operation
            );

            return ProviderOperationResponse.success();

        } catch (IllegalStateException exception) {

            /*
             * Invalid provider state is itself a durable provider
             * result.
             *
             * Example:
             *
             *     capture(CAPTURED)
             *
             * The same idempotency key must replay the same failure.
             */
            MockProviderOperation operation =
                    MockProviderOperation.failure(
                            operationType,
                            idempotencyKey,
                            requestHash,
                            providerPaymentId,
                            "INVALID_PROVIDER_STATE",
                            exception.getMessage()
                    );

            operationRepository.saveAndFlush(
                    operation
            );

            return ProviderOperationResponse.failure(
                    "INVALID_PROVIDER_STATE",
                    exception.getMessage()
            );
        }
    }

    private ProviderOperationResponse
    findOperationResult(
            MockProviderOperationType operationType,
            String idempotencyKey,
            String requestHash
    ) {

        return operationRepository
                .findByOperationTypeAndIdempotencyKey(
                        operationType,
                        idempotencyKey
                )
                .map(operation -> {

                    verifyRequestHash(
                            operation,
                            requestHash
                    );

                    return new ProviderOperationResponse(
                            operation.isSuccessful(),
                            operation.getFailureCode(),
                            operation.getFailureMessage()
                    );
                })
                .orElse(null);
    }

    // ================================================================
    // IDEMPOTENCY VALIDATION
    // ================================================================

    private void verifyRequestHash(
            MockProviderOperation operation,
            String requestHash
    ) {

        if (!operation.getRequestHash()
                .equals(requestHash)) {

            throw new PaymentProviderException(
                    "Provider idempotency key was reused "
                            + "with a different request"
            );
        }
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    private void validateAuthorizationRequest(
            ProviderAuthorizationRequest request,
            String idempotencyKey
    ) {

        if (request == null) {

            throw new PaymentProviderException(
                    "Provider authorization request is required"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new PaymentProviderException(
                    "Provider idempotency key is required"
            );
        }
    }

    private void validateOperation(
            String providerPaymentId,
            String idempotencyKey
    ) {

        if (providerPaymentId == null
                || providerPaymentId.isBlank()) {

            throw new PaymentProviderException(
                    "Provider payment ID is required"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new PaymentProviderException(
                    "Provider idempotency key is required"
            );
        }
    }

    // ================================================================
    // INTERNAL REQUEST FINGERPRINT
    // ================================================================

    private record ProviderOperationRequest(
            String providerPaymentId
    ) {
    }
}