package com.microservice.orders.order.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.exception.EmptyOrderException;
import com.microservice.orders.exception.InventoryReservationException;
import com.microservice.orders.exception.InvalidOrderStateException;
import com.microservice.orders.exception.OrderNotFoundException;
import com.microservice.orders.exception.PaymentFailedException;
import com.microservice.orders.integration.payment.PaymentClient;
import com.microservice.orders.integration.payment.dto.PaymentRequest;
import com.microservice.orders.integration.payment.dto.PaymentResponse;
import com.microservice.orders.integration.product.ProductClient;
import com.microservice.orders.integration.product.dto.ProductSnapshot;
import com.microservice.orders.inventory.InventoryClient;
import com.microservice.orders.inventory.dto.response.InventoryOperationResponse;
import com.microservice.orders.inventory.dto.response.ReservationResponse;
import com.microservice.orders.idempotency.service.IdempotencyResult;
import com.microservice.orders.idempotency.service.IdempotencyService;
import com.microservice.orders.idempotency.service.RequestHashService;
import com.microservice.orders.config.properties.OrderProperties;
import com.microservice.orders.order.dto.request.CancelOrderRequest;
import com.microservice.orders.order.dto.request.CreateOrderRequest;
import com.microservice.orders.order.dto.request.OrderItemRequest;
import com.microservice.orders.order.dto.response.OrderPageResponse;
import com.microservice.orders.order.dto.response.OrderResponse;
import com.microservice.orders.order.entity.Order;
import com.microservice.orders.order.entity.OrderItem;
import com.microservice.orders.order.entity.OrderStatus;
import com.microservice.orders.order.entity.PaymentMethod;
import com.microservice.orders.order.entity.PaymentStatus;
import com.microservice.orders.order.mapper.OrderMapper;
import com.microservice.orders.order.repository.OrderRepository;
import com.microservice.orders.security.CurrentUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderServiceImpl
        implements OrderService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OrderServiceImpl.class
            );

    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUser currentUser;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;
    private final OrderProperties orderProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderServiceImpl(
            CurrentUser currentUser,
            ProductClient productClient,
            InventoryClient inventoryClient,
            PaymentClient paymentClient,
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            OrderProperties orderProperties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.currentUser =
                currentUser;

        this.productClient =
                productClient;

        this.inventoryClient =
                inventoryClient;

        this.paymentClient =
                paymentClient;

        this.orderRepository =
                orderRepository;

        this.orderMapper =
                orderMapper;

        this.idempotencyService =
                idempotencyService;

        this.requestHashService =
                requestHashService;

        this.orderProperties =
                orderProperties;

        this.objectMapper =
                objectMapper;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );
    }

    // ================================================================
    // CREATE ORDER
    // ================================================================

    @Override
    public OrderResponse createOrder(
            CreateOrderRequest request,
            String idempotencyKey
    ) {
        validateRequest(request);

        UUID userId =
                currentUser.userId();

        String normalizedKey =
                validateIdempotencyKey(
                        idempotencyKey
                );

        String requestHash =
                requestHashService.hash(
                        request
                );

        IdempotencyResult idempotencyResult =
                idempotencyService.begin(
                        userId,
                        normalizedKey,
                        requestHash
                );

        if (!idempotencyResult.shouldProcess()) {
            return deserializeOrderResponse(
                    idempotencyResult.responseBody()
            );
        }

        UUID orderId =
                deterministicOrderId(
                        userId,
                        normalizedKey
                );

        var existingOrder =
                transactionTemplate.execute(
                        status ->
                                orderRepository
                                        .findByOrderIdAndUserIdWithItems(
                                                orderId,
                                                userId
                                        )
                                        .orElse(null)
                );

        if (existingOrder != null) {

            /*
             * The idempotency record may have expired after a process crash
             * or a downstream timeout. The deterministic order ID lets us
             * recover the durable local state and safely continue the saga
             * instead of returning a half-finished order forever.
             */
            OrderResponse response =
                    recoverExistingOrder(
                            existingOrder,
                            userId
                    );

            safelyCompleteIdempotency(
                    userId,
                    normalizedKey,
                    requestHash,
                    response
            );

            return response;
        }

        List<ResolvedItem> resolvedItems =
                resolveProducts(request);

        validateSameCurrency(
                resolvedItems
        );

        List<ReservationHandle> reservations =
                new ArrayList<>(
                        resolvedItems.size()
                );

        try {

            /*
             * ------------------------------------------------------------
             * STEP 1
             *
             * Reserve inventory.
             * ------------------------------------------------------------
             */
            for (ResolvedItem item :
                    resolvedItems) {

                String inventoryKey =
                        inventoryIdempotencyKey(
                                orderId,
                                item.request().productId()
                        );

                ReservationResponse reservation =
                        inventoryClient.reserve(
                                orderId,
                                item.request().productId(),
                                item.request().quantity(),
                                inventoryKey
                        );

                validateReservation(
                        reservation,
                        orderId,
                        item.request()
                );

                reservations.add(
                        new ReservationHandle(
                                reservation.reservationId(),
                                item
                        )
                );
            }

            /*
             * ------------------------------------------------------------
             * STEP 2
             *
             * Persist Order = PLACED.
             *
             * No remote payment/provider call occurs in this transaction.
             * ------------------------------------------------------------
             */
            OrderResponse placedOrder =
                    persistPlacedOrder(
                            orderId,
                            userId,
                            request,
                            resolvedItems,
                            reservations
                    );

            /*
             * ------------------------------------------------------------
             * STEP 3
             *
             * COD:
             *
             * no Payment Service operation.
             * ------------------------------------------------------------
             */
            if (request.paymentMethod()
                    == PaymentMethod.COD) {

                commitReservations(
                        orderId,
                        reservations
                );

                OrderResponse confirmed =
                        markOrderConfirmed(
                                orderId,
                                userId
                        );

                safelyCompleteIdempotency(
                        userId,
                        normalizedKey,
                        requestHash,
                        confirmed
                );

                return confirmed;
            }

            /*
             * ------------------------------------------------------------
             * STEP 4
             *
             * Online payment authorization.
             * ------------------------------------------------------------
             */
            PaymentRequest paymentRequest =
                    new PaymentRequest(
                            orderId,
                            userId,
                            request.paymentMethod(),
                            placedOrder.totalAmount(),
                            placedOrder.currency()
                    );

            String authorizationKey =
                    paymentIdempotencyKey(
                            orderId,
                            "authorize"
                    );

            PaymentResponse authorization;

            try {
                authorization =
                        paymentClient.authorize(
                                paymentRequest,
                                authorizationKey
                        );
            } catch (DownstreamServiceException exception) {

                /*
                 * Unknown outcome.
                 *
                 * Payment Service may have successfully authorized
                 * the payment even though this request timed out.
                 *
                 * DO NOT release inventory.
                 * DO NOT cancel the order.
                 */
                log.error(
                        "Unknown payment authorization outcome for order {}",
                        orderId,
                        exception
                );

                throw exception;
            }

            validatePaymentResponse(
                    authorization,
                    orderId,
                    userId
            );

            if ("FAILED".equals(
                    authorization.status()
            )) {

                /*
                 * Known payment failure.
                 *
                 * Safe to cancel and release reservations.
                 */
                markPaymentFailedAndCancel(
                        orderId,
                        userId,
                        authorization.failureMessage()
                );

                releaseReservedInventory(
                        orderId,
                        reservations.stream()
                                .map(ReservationHandle::reservationId)
                                .toList()
                );

                throw new PaymentFailedException(
                        authorization.failureMessage() == null
                                ? "Payment authorization failed"
                                : authorization.failureMessage(),
                        authorization.failureCode()
                );
            }

            if (!"AUTHORIZED".equals(
                    authorization.status()
            )) {
                throw new DownstreamServiceException(
                        "Payment Service returned unexpected authorization status: "
                                + authorization.status()
                );
            }

            attachAuthorizedPayment(
                    orderId,
                    userId,
                    authorization.paymentId()
            );

            /*
             * ------------------------------------------------------------
             * STEP 5
             *
             * Capture payment.
             * ------------------------------------------------------------
             */
            PaymentResponse captured;

            try {
                captured =
                        paymentClient.capture(
                                authorization.paymentId(),
                                paymentIdempotencyKey(
                                        orderId,
                                        "capture"
                                )
                        );
            } catch (DownstreamServiceException exception) {

                /*
                 * Unknown capture outcome.
                 *
                 * Never blindly refund/release because the provider
                 * may have captured the payment.
                 */
                log.error(
                        "Unknown payment capture outcome for order {}",
                        orderId,
                        exception
                );

                throw exception;
            }

            validatePaymentResponse(
                    captured,
                    orderId,
                    userId
            );

            if (!"CAPTURED".equals(
                    captured.status()
            )) {

                throw new DownstreamServiceException(
                        "Payment Service returned unexpected capture status: "
                                + captured.status()
                );
            }

            markPaymentCaptured(
                    orderId,
                    userId
            );

            /*
             * ------------------------------------------------------------
             * STEP 6
             *
             * Commit inventory.
             *
             * IMPORTANT:
             * Payment is already captured here.
             *
             * If inventory commit fails, we attempt a refund, but we
             * do not pretend that release can undo a committed reservation.
             * ------------------------------------------------------------
             */
            try {

                commitReservations(
                        orderId,
                        reservations
                );

            } catch (RuntimeException inventoryException) {

                log.error(
                        "Inventory commit failed after payment capture for order {}",
                        orderId,
                        inventoryException
                );

                attemptRefundAfterInventoryFailure(
                        orderId,
                        userId,
                        authorization.paymentId()
                );

                throw new DownstreamServiceException(
                        "Inventory could not be committed after payment capture. "
                                + "Order requires reconciliation.",
                        inventoryException
                );
            }

            /*
             * ------------------------------------------------------------
             * STEP 7
             *
             * Everything succeeded.
             * ------------------------------------------------------------
             */
            OrderResponse confirmed =
                    markOrderConfirmed(
                            orderId,
                            userId
                    );

            safelyCompleteIdempotency(
                    userId,
                    normalizedKey,
                    requestHash,
                    confirmed
            );

            return confirmed;

        } catch (PaymentFailedException exception) {

            /*
             * Known payment failure already performed its own
             * compensation.
             */
            throw exception;

        } catch (RuntimeException exception) {

            /*
             * Only compensate reservations when the failure occurred
             * before payment reached an unknown/captured state.
             *
             * We deliberately do NOT blindly compensate on a generic
             * downstream payment failure because the provider outcome
             * may be unknown.
             */
            if (!(exception instanceof DownstreamServiceException)) {
                compensateReservations(
                        orderId,
                        reservations,
                        exception
                );
            }

            throw exception;
        }
    }

    // ================================================================
    // RECOVERY OF A DURABLE ORDER AFTER AN ABANDONED REQUEST
    // ================================================================

    private OrderResponse recoverExistingOrder(
            Order existingOrder,
            UUID userId
    ) {
        UUID orderId = existingOrder.getOrderId();

        /*
         * Terminal local states are safe to replay.
         */
        if (existingOrder.getStatus() == OrderStatus.CANCELLED
                || existingOrder.getStatus() == OrderStatus.CONFIRMED) {
            return transactionTemplate.execute(
                    status -> orderMapper.toResponse(existingOrder)
            );
        }

        if (existingOrder.getStatus() != OrderStatus.PLACED) {
            throw new DownstreamServiceException(
                    "Order " + orderId
                            + " is in an unrecoverable orchestration state: "
                            + existingOrder.getStatus()
            );
        }

        List<UUID> reservationIds =
                existingOrder.getItems()
                        .stream()
                        .map(OrderItem::getInventoryReservationId)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        if (existingOrder.getPaymentMethod() == PaymentMethod.COD
                || existingOrder.getPaymentStatus() == PaymentStatus.NOT_REQUIRED) {
            commitReservationIds(orderId, reservationIds);
            return markOrderConfirmed(orderId, userId);
        }

        switch (existingOrder.getPaymentStatus()) {
            case PENDING -> {
                PaymentResponse authorization;

                try {
                    authorization = paymentClient.authorize(
                            new PaymentRequest(
                                    orderId,
                                    userId,
                                    existingOrder.getPaymentMethod(),
                                    existingOrder.getTotalAmount(),
                                    existingOrder.getCurrency()
                            ),
                            paymentIdempotencyKey(orderId, "authorize")
                    );
                } catch (DownstreamServiceException exception) {
                    log.error(
                            "Unknown payment authorization outcome during recovery for order {}",
                            orderId,
                            exception
                    );
                    throw exception;
                }

                validatePaymentResponse(
                        authorization,
                        orderId,
                        userId
                );

                if ("FAILED".equals(authorization.status())) {
                    markPaymentFailedAndCancel(
                            orderId,
                            userId,
                            authorization.failureMessage()
                    );
                    releaseReservedInventory(
                            orderId,
                            reservationIds
                    );
                    return getOrder(orderId);
                }

                if (!"AUTHORIZED".equals(authorization.status())) {
                    throw new DownstreamServiceException(
                            "Payment Service returned unexpected recovery authorization status: "
                                    + authorization.status()
                    );
                }

                attachAuthorizedPayment(
                        orderId,
                        userId,
                        authorization.paymentId()
                );
            }

            case AUTHORIZED -> {
                // Payment is already locally attached and can be captured below.
            }

            case CAPTURED -> {
                commitReservationIds(orderId, reservationIds);
                return markOrderConfirmed(orderId, userId);
            }

            case FAILED, VOIDED, REFUNDED -> {
                releaseReservedInventory(
                        orderId,
                        reservationIds
                );

                transactionTemplate.executeWithoutResult(status -> {
                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(() ->
                                            new OrderNotFoundException(
                                                    "Order not found: " + orderId
                                            )
                                    );

                    if (order.getStatus() != OrderStatus.CANCELLED) {
                        order.cancel("Recovered after terminal payment state "
                                + order.getPaymentStatus());
                        orderRepository.flush();
                    }
                });

                return getOrder(orderId);
            }

            case NOT_REQUIRED -> {
                commitReservationIds(orderId, reservationIds);
                return markOrderConfirmed(orderId, userId);
            }
        }

        /*
         * At this point the local payment state is AUTHORIZED. Capture with
         * the same deterministic idempotency key used by the original request.
         * If the original capture succeeded but the response was lost, Payment
         * Service replays CAPTURED instead of charging twice.
         */
        PaymentResponse captured;

        try {
            captured = paymentClient.capture(
                    orderId == null ? null : getRequiredPaymentId(orderId, userId),
                    paymentIdempotencyKey(orderId, "capture")
            );
        } catch (DownstreamServiceException exception) {
            log.error(
                    "Unknown payment capture outcome during recovery for order {}",
                    orderId,
                    exception
            );
            throw exception;
        }

        validatePaymentResponse(
                captured,
                orderId,
                userId
        );

        if (!"CAPTURED".equals(captured.status())) {
            throw new DownstreamServiceException(
                    "Payment Service returned unexpected recovery capture status: "
                            + captured.status()
            );
        }

        markPaymentCaptured(
                orderId,
                userId
        );

        try {
            commitReservationIds(
                    orderId,
                    reservationIds
            );
        } catch (RuntimeException inventoryException) {
            attemptRefundAfterInventoryFailure(
                    orderId,
                    userId,
                    captured.paymentId()
            );

            throw new DownstreamServiceException(
                    "Inventory could not be committed during order recovery. "
                            + "Order requires reconciliation.",
                    inventoryException
            );
        }

        return markOrderConfirmed(
                orderId,
                userId
        );
    }

    private UUID getRequiredPaymentId(
            UUID orderId,
            UUID userId
    ) {
        return transactionTemplate.execute(status ->
                orderRepository
                        .findByOrderIdAndUserId(
                                orderId,
                                userId
                        )
                        .map(Order::getPaymentId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Order has no payment ID: " + orderId
                                )
                        )
        );
    }

    private void commitReservationIds(
            UUID orderId,
            List<UUID> reservationIds
    ) {
        List<ReservationHandle> handles =
                reservationIds
                        .stream()
                        .map(id -> new ReservationHandle(id, null))
                        .toList();

        commitReservations(
                orderId,
                handles
        );
    }

    // ================================================================
    // GET ORDER
    // ================================================================

    @Override
    public OrderResponse getOrder(
            UUID orderId
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException(
                    "Order ID must not be null"
            );
        }

        UUID userId =
                currentUser.userId();

        return transactionTemplate.execute(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserId(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    return orderMapper.toResponse(
                            order
                    );
                }
        );
    }

    // ================================================================
    // GET ORDER HISTORY
    // ================================================================

    @Override
    public OrderPageResponse getOrders(
            int page,
            int size
    ) {
        validatePagination(
                page,
                size
        );

        UUID userId =
                currentUser.userId();

        return transactionTemplate.execute(
                status -> {

                    Page<Order> result =
                            orderRepository
                                    .findByUserIdOrderByCreatedAtDesc(
                                            userId,
                                            PageRequest.of(
                                                    page,
                                                    size
                                            )
                                    );

                    List<
                            com.microservice.orders.order.dto.response.OrderSummaryResponse
                            > content =
                            result.getContent()
                                    .stream()
                                    .map(
                                            orderMapper::toSummaryResponse
                                    )
                                    .toList();

                    return new OrderPageResponse(
                            content,
                            result.getNumber(),
                            result.getSize(),
                            result.getTotalElements(),
                            result.getTotalPages(),
                            result.isFirst(),
                            result.isLast(),
                            result.isEmpty()
                    );
                }
        );
    }

    // ================================================================
    // CANCEL ORDER
    // ================================================================

    @Override
    public OrderResponse cancelOrder(
            UUID orderId,
            CancelOrderRequest request
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException(
                    "Order ID must not be null"
            );
        }

        UUID userId =
                currentUser.userId();

        String reason =
                request == null
                        ? null
                        : request.reason();

        CancellationPlan plan =
                transactionTemplate.execute(
                        status -> {

                            Order order =
                                    orderRepository
                                            .findByOrderIdAndUserIdForUpdate(
                                                    orderId,
                                                    userId
                                            )
                                            .orElseThrow(
                                                    () ->
                                                            new OrderNotFoundException(
                                                                    "Order not found: "
                                                                            + orderId
                                                            )
                                            );

                            validateCancellation(
                                    order
                            );

                            List<UUID> reservationIds =
                                    order.getItems()
                                            .stream()
                                            .map(
                                                    OrderItem::getInventoryReservationId
                                            )
                                            .filter(
                                                    java.util.Objects::nonNull
                                            )
                                            .toList();

                            return new CancellationPlan(
                                    order.getOrderId(),
                                    order.getPaymentId(),
                                    order.getPaymentStatus(),
                                    reason,
                                    reservationIds
                            );
                        }
                );

        if (plan == null) {
            throw new IllegalStateException(
                    "Cancellation plan could not be created"
            );
        }

        /*
         * ------------------------------------------------------------
         * Payment compensation MUST happen before the order becomes
         * cancelled.
         *
         * No DB transaction is held here.
         * ------------------------------------------------------------
         */
        if (plan.paymentId() != null) {

            if (plan.paymentStatus()
                    == PaymentStatus.AUTHORIZED) {

                try {

                    PaymentResponse response =
                            paymentClient.voidPayment(
                                    plan.paymentId(),
                                    paymentIdempotencyKey(
                                            orderId,
                                            "void"
                                    )
                            );

                    if (!"VOIDED".equals(
                            response.status()
                    )) {
                        throw new DownstreamServiceException(
                                "Payment Service did not void payment. "
                                        + "Current status: "
                                        + response.status()
                        );
                    }

                } catch (RuntimeException exception) {

                    /*
                     * Do not cancel the order if payment void outcome
                     * is unknown.
                     */
                    throw new DownstreamServiceException(
                            "Unable to safely void payment for order "
                                    + orderId,
                            exception
                    );
                }

            } else if (plan.paymentStatus()
                    == PaymentStatus.CAPTURED) {

                try {

                    PaymentResponse response =
                            paymentClient.refund(
                                    plan.paymentId(),
                                    paymentIdempotencyKey(
                                            orderId,
                                            "refund"
                                    )
                            );

                    if (!"REFUNDED".equals(
                            response.status()
                    )) {
                        throw new DownstreamServiceException(
                                "Payment Service did not refund payment. "
                                        + "Current status: "
                                        + response.status()
                        );
                    }

                } catch (RuntimeException exception) {

                    throw new DownstreamServiceException(
                            "Unable to safely refund payment for order "
                                    + orderId,
                            exception
                    );
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * Payment compensation succeeded.
         *
         * Now release only reservations that are still releasable.
         * ------------------------------------------------------------
         */
        releaseReservedInventory(
                orderId,
                plan.reservationIds()
        );

        /*
         * ------------------------------------------------------------
         * Finally mark the order cancelled locally.
         * ------------------------------------------------------------
         */
        return transactionTemplate.execute(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    if (order.getStatus()
                            == OrderStatus.CANCELLED) {

                        return orderMapper.toResponse(
                                order
                        );
                    }

                    /*
                     * Payment state is finalized locally.
                     */
                    if (plan.paymentStatus()
                            == PaymentStatus.AUTHORIZED) {

                        order.markPaymentVoided();

                    } else if (plan.paymentStatus()
                            == PaymentStatus.CAPTURED) {

                        order.markPaymentRefunded();
                    }

                    order.cancel(
                            reason
                    );

                    orderRepository.flush();

                    return orderMapper.toResponse(
                            order
                    );
                }
        );
    }

    // ================================================================
    // PERSIST PLACED ORDER
    // ================================================================

    private OrderResponse persistPlacedOrder(
            UUID orderId,
            UUID userId,
            CreateOrderRequest request,
            List<ResolvedItem> resolvedItems,
            List<ReservationHandle> reservations
    ) {
        return transactionTemplate.execute(
                status -> {

                    Order order =
                            Order.create(
                                    orderId,
                                    userId,
                                    request.paymentMethod(),
                                    resolvedItems
                                            .get(0)
                                            .snapshot()
                                            .currency()
                            );

                    for (ReservationHandle reservation :
                            reservations) {

                        ResolvedItem item =
                                reservation.item();

                        order.addItem(
                                item.snapshot().productId(),
                                item.snapshot().productName(),
                                item.snapshot().unitPrice(),
                                item.snapshot().currency(),
                                item.request().quantity()
                        );

                        OrderItem orderItem =
                                order.getItems()
                                        .get(
                                                order.getItems().size() - 1
                                        );

                        orderItem.attachInventoryReservation(
                                reservation.reservationId()
                        );
                    }

                    order.markPlaced();

                    orderRepository.save(
                            order
                    );

                    orderRepository.flush();

                    return orderMapper.toResponse(
                            order
                    );
                }
        );
    }

    // ================================================================
    // PAYMENT
    // ================================================================

    private void attachAuthorizedPayment(
            UUID orderId,
            UUID userId,
            UUID paymentId
    ) {
        transactionTemplate.executeWithoutResult(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    order.attachPayment(
                            paymentId
                    );

                    order.markPaymentAuthorized();

                    orderRepository.flush();
                }
        );
    }

    private void markPaymentCaptured(
            UUID orderId,
            UUID userId
    ) {
        transactionTemplate.executeWithoutResult(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    order.markPaymentCaptured();

                    orderRepository.flush();
                }
        );
    }

    private void markPaymentFailedAndCancel(
            UUID orderId,
            UUID userId,
            String reason
    ) {
        transactionTemplate.executeWithoutResult(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    order.markPaymentFailed();

                    order.cancel(
                            reason
                    );

                    orderRepository.flush();
                }
        );
    }

    private OrderResponse markOrderConfirmed(
            UUID orderId,
            UUID userId
    ) {
        return transactionTemplate.execute(
                status -> {

                    Order order =
                            orderRepository
                                    .findByOrderIdAndUserIdForUpdate(
                                            orderId,
                                            userId
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new OrderNotFoundException(
                                                            "Order not found: "
                                                                    + orderId
                                                    )
                                    );

                    order.confirm();

                    orderRepository.flush();

                    return orderMapper.toResponse(
                            order
                    );
                }
        );
    }

    private void attemptRefundAfterInventoryFailure(
            UUID orderId,
            UUID userId,
            UUID paymentId
    ) {
        try {

            PaymentResponse refund =
                    paymentClient.refund(
                            paymentId,
                            paymentIdempotencyKey(
                                    orderId,
                                    "refund-after-inventory-failure"
                            )
                    );

            if (!"REFUNDED".equals(
                    refund.status()
            )) {
                log.error(
                        "Refund did not reach REFUNDED for order {}. "
                                + "Payment status={}",
                        orderId,
                        refund.status()
                );
                return;
            }

            transactionTemplate.executeWithoutResult(
                    status -> {

                        Order order =
                                orderRepository
                                        .findByOrderIdAndUserIdForUpdate(
                                                orderId,
                                                userId
                                        )
                                        .orElse(null);

                        if (order == null) {
                            return;
                        }

                        if (order.getPaymentStatus()
                                == PaymentStatus.CAPTURED) {

                            order.markPaymentRefunded();

                            orderRepository.flush();
                        }
                    }
            );

        } catch (RuntimeException exception) {

            /*
             * Payment outcome is now itself uncertain.
             *
             * Do not attempt inventory release because some inventory
             * reservations may already have been committed.
             */
            log.error(
                    "CRITICAL: refund failed/unknown after inventory "
                            + "commit failure for order {}. "
                            + "Manual/reconciliation processing required.",
                    orderId,
                    exception
            );
        }
    }

    // ================================================================
    // INVENTORY
    // ================================================================

    private void commitReservations(
            UUID orderId,
            List<ReservationHandle> reservations
    ) {
        for (ReservationHandle reservation :
                reservations) {

            InventoryOperationResponse response =
                    inventoryClient.commit(
                            reservation.reservationId()
                    );

            if (response == null) {
                throw new InventoryReservationException(
                        "Inventory Service returned an empty commit response"
                );
            }

            if (!"COMMITTED".equals(
                    response.status()
            )) {
                throw new InventoryReservationException(
                        "Inventory reservation "
                                + reservation.reservationId()
                                + " was not committed. Status: "
                                + response.status()
                );
            }
        }
    }

    private void releaseReservedInventory(
            UUID orderId,
            List<UUID> reservationIds
    ) {
        RuntimeException firstFailure =
                null;

        for (UUID reservationId :
                reservationIds) {

            try {

                InventoryOperationResponse response =
                        inventoryClient.release(
                                reservationId
                        );

                if (response == null) {
                    throw new DownstreamServiceException(
                            "Inventory Service returned an empty release response"
                    );
                }

                /*
                 * RELEASED is the only successful result.
                 *
                 * If the Product Service says the reservation was already
                 * COMMITTED, we must not pretend release reversed stock.
                 */
                if (!"RELEASED".equals(
                        response.status()
                )
                        && !"EXPIRED".equals(
                        response.status()
                )) {

                    throw new InventoryReservationException(
                            "Reservation "
                                    + reservationId
                                    + " could not be released. Status: "
                                    + response.status()
                    );
                }

                log.info(
                        "Released inventory reservation {} for cancelled order {}",
                        reservationId,
                        orderId
                );

            } catch (RuntimeException exception) {

                if (firstFailure == null) {
                    firstFailure =
                            exception;
                } else {
                    firstFailure.addSuppressed(
                            exception
                    );
                }

                log.error(
                        "Failed to release inventory reservation {} "
                                + "for cancelled order {}",
                        reservationId,
                        orderId,
                        exception
                );
            }
        }

        if (firstFailure != null) {
            throw new DownstreamServiceException(
                    "Unable to release one or more inventory reservations "
                            + "for order "
                            + orderId,
                    firstFailure
            );
        }
    }

    private void compensateReservations(
            UUID orderId,
            List<ReservationHandle> reservations,
            RuntimeException originalException
    ) {
        if (reservations.isEmpty()) {
            return;
        }

        try {

            releaseReservedInventory(
                    orderId,
                    reservations.stream()
                            .map(
                                    ReservationHandle::reservationId
                            )
                            .toList()
            );

        } catch (RuntimeException compensationException) {

            originalException.addSuppressed(
                    compensationException
            );

            log.error(
                    "Inventory compensation failed",
                    compensationException
            );
        }
    }

    // ================================================================
    // PRODUCT RESOLUTION
    // ================================================================

    private List<ResolvedItem> resolveProducts(
            CreateOrderRequest request
    ) {
        if (request.items().size()
                > orderProperties.maxItemsPerOrder()) {

            throw new IllegalArgumentException(
                    "Order cannot contain more than "
                            + orderProperties.maxItemsPerOrder()
                            + " items"
            );
        }

        Set<UUID> productIds =
                new HashSet<>();

        List<ResolvedItem> resolved =
                new ArrayList<>(
                        request.items().size()
                );

        for (OrderItemRequest item :
                request.items()) {

            if (item == null) {
                throw new IllegalArgumentException(
                        "Order item must not be null"
                );
            }

            if (item.productId() == null) {
                throw new IllegalArgumentException(
                        "Product ID must not be null"
                );
            }

            if (item.quantity() == null
                    || item.quantity() <= 0) {

                throw new IllegalArgumentException(
                        "Order item quantity must be positive"
                );
            }

            if (item.quantity()
                    > orderProperties.maxQuantityPerItem()) {

                throw new IllegalArgumentException(
                        "Quantity for product "
                                + item.productId()
                                + " cannot exceed "
                                + orderProperties.maxQuantityPerItem()
                );
            }

            if (!productIds.add(
                    item.productId()
            )) {

                throw new IllegalArgumentException(
                        "Duplicate product in order: "
                                + item.productId()
                );
            }

            ProductSnapshot snapshot =
                    productClient.getProductSnapshot(
                            item.productId()
                    );

            if (snapshot == null) {
                throw new DownstreamServiceException(
                        "Product Service returned no snapshot for product "
                                + item.productId()
                );
            }

            resolved.add(
                    new ResolvedItem(
                            item,
                            snapshot
                    )
            );
        }

        return resolved;
    }

    private void validateSameCurrency(
            List<ResolvedItem> resolvedItems
    ) {
        if (resolvedItems.isEmpty()) {
            throw new EmptyOrderException(
                    "Order must contain at least one item"
            );
        }

        String currency =
                normalizeCurrency(
                        resolvedItems
                                .get(0)
                                .snapshot()
                                .currency()
                );

        for (ResolvedItem item :
                resolvedItems) {

            String itemCurrency =
                    normalizeCurrency(
                            item.snapshot().currency()
                    );

            if (!currency.equals(
                    itemCurrency
            )) {

                throw new IllegalArgumentException(
                        "All order items must use the same currency"
                );
            }
        }
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    private void validateRequest(
            CreateOrderRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Order request must not be null"
            );
        }

        if (request.items() == null
                || request.items().isEmpty()) {

            throw new EmptyOrderException(
                    "Order must contain at least one item"
            );
        }

        if (request.paymentMethod() == null) {
            throw new IllegalArgumentException(
                    "Payment method is required"
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
                > orderProperties.idempotencyKeyMaxLength()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed "
                            + orderProperties.idempotencyKeyMaxLength()
                            + " characters"
            );
        }

        return normalized;
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must not be negative"
            );
        }

        if (size < 1
                || size > MAX_PAGE_SIZE) {

            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private void validateReservation(
            ReservationResponse reservation,
            UUID orderId,
            OrderItemRequest request
    ) {
        if (reservation == null) {
            throw new DownstreamServiceException(
                    "Inventory Service returned no reservation"
            );
        }

        if (!orderId.equals(
                reservation.orderId()
        )) {
            throw new InventoryReservationException(
                    "Inventory reservation returned the wrong order ID"
            );
        }

        if (!request.productId().equals(
                reservation.productId()
        )) {
            throw new InventoryReservationException(
                    "Inventory reservation returned the wrong product ID"
            );
        }

        if (!Integer.valueOf(
                request.quantity()
        ).equals(
                reservation.quantity()
        )) {
            throw new InventoryReservationException(
                    "Inventory reservation returned the wrong quantity"
            );
        }

        if (!"RESERVED".equals(
                reservation.status()
        )) {
            throw new InventoryReservationException(
                    "Inventory reservation was not created in RESERVED state"
            );
        }
    }

    private void validatePaymentResponse(
            PaymentResponse response,
            UUID orderId,
            UUID userId
    ) {
        if (response == null) {
            throw new DownstreamServiceException(
                    "Payment Service returned an empty response"
            );
        }

        if (!orderId.equals(
                response.orderId()
        )) {
            throw new DownstreamServiceException(
                    "Payment Service returned a payment for another order"
            );
        }

        if (!userId.equals(
                response.userId()
        )) {
            throw new DownstreamServiceException(
                    "Payment Service returned a payment for another user"
            );
        }

        if (response.paymentId() == null) {
            throw new DownstreamServiceException(
                    "Payment Service returned no payment ID"
            );
        }
    }

    private void validateCancellation(
            Order order
    ) {
        if (order.getStatus()
                == OrderStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Order is already cancelled"
            );
        }

        /*
         * Once inventory has been committed, the current Product Service
         * API has no "restock committed reservation" operation.
         *
         * Therefore CONFIRMED and later states cannot be cancelled here.
         */
        if (order.getStatus()
                != OrderStatus.PLACED) {

            throw new IllegalStateException(
                    "Only PLACED orders can currently be cancelled"
            );
        }

        PaymentStatus paymentStatus =
                order.getPaymentStatus();

        if (paymentStatus
                == PaymentStatus.PENDING) {

            /*
             * For an online order, PLACED + PENDING means the authorization
             * outcome is unresolved. The remote Payment Service may already
             * have authorized the payment even if Order Service did not receive
             * the response. Cancelling here could therefore race with a charge.
             *
             * COD orders use NOT_REQUIRED and are handled separately above.
             */
            throw new InvalidOrderStateException(
                    "Online payment authorization outcome is unresolved; "
                            + "order must be reconciled before cancellation"
            );
        }

        if (paymentStatus
                == PaymentStatus.NOT_REQUIRED) {

            return;
        }

        if (paymentStatus
                == PaymentStatus.AUTHORIZED
                || paymentStatus
                == PaymentStatus.CAPTURED) {

            if (order.getPaymentId() == null) {
                throw new IllegalStateException(
                        "Payment ID is required for payment compensation"
                );
            }

            return;
        }

        if (paymentStatus
                == PaymentStatus.FAILED
                || paymentStatus
                == PaymentStatus.VOIDED
                || paymentStatus
                == PaymentStatus.REFUNDED) {

            return;
        }

        throw new IllegalStateException(
                "Unsupported payment state for cancellation: "
                        + paymentStatus
        );
    }

    // ================================================================
    // IDEMPOTENCY
    // ================================================================

    private void safelyCompleteIdempotency(
            UUID userId,
            String key,
            String requestHash,
            OrderResponse response
    ) {
        try {

            idempotencyService.complete(
                    userId,
                    key,
                    requestHash,
                    201,
                    objectMapper.writeValueAsString(
                            response
                    )
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to serialize order response",
                    exception
            );
        }
    }

    private OrderResponse deserializeOrderResponse(
            String responseBody
    ) {
        if (responseBody == null
                || responseBody.isBlank()) {

            throw new IllegalStateException(
                    "Stored order response is empty"
            );
        }

        try {

            return objectMapper.readValue(
                    responseBody,
                    OrderResponse.class
            );

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Unable to deserialize stored order response",
                    exception
            );
        }
    }

    // ================================================================
    // IDS
    // ================================================================

    private UUID deterministicOrderId(
            UUID userId,
            String idempotencyKey
    ) {
        String material =
                "order:"
                        + userId
                        + ":"
                        + idempotencyKey;

        return UUID.nameUUIDFromBytes(
                material.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private String inventoryIdempotencyKey(
            UUID orderId,
            UUID productId
    ) {
        return "order:"
                + orderId
                + ":product:"
                + productId;
    }

    private String paymentIdempotencyKey(
            UUID orderId,
            String operation
    ) {
        return "order:"
                + orderId
                + ":payment:"
                + operation;
    }

    private String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        return currency
                .trim()
                .toUpperCase();
    }

    // ================================================================
    // INTERNAL RECORDS
    // ================================================================

    private record ResolvedItem(
            OrderItemRequest request,
            ProductSnapshot snapshot
    ) {
    }

    private record ReservationHandle(
            UUID reservationId,
            ResolvedItem item
    ) {
    }

    private record CancellationPlan(
            UUID orderId,
            UUID paymentId,
            PaymentStatus paymentStatus,
            String reason,
            List<UUID> reservationIds
    ) {
    }
}