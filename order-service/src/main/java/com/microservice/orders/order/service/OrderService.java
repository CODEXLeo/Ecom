package com.microservice.orders.order.service;

import java.util.UUID;

import com.microservice.orders.order.dto.request.CancelOrderRequest;
import com.microservice.orders.order.dto.request.CreateOrderRequest;
import com.microservice.orders.order.dto.response.OrderPageResponse;
import com.microservice.orders.order.dto.response.OrderResponse;

public interface OrderService {

    /**
     * Creates an order for the authenticated user.
     *
     * The operation is idempotent and requires an Idempotency-Key.
     *
     * The operation is intentionally all-or-nothing:
     *
     * 1. Validate the request.
     * 2. Resolve product snapshots.
     * 3. Reserve inventory for every item.
     * 4. Persist the order.
     * 5. Mark the order PLACED.
     * 6. Complete the idempotency record.
     *
     * If inventory reservation fails after previous reservations
     * succeeded, the previous reservations are released.
     */
    OrderResponse createOrder(
            CreateOrderRequest request,
            String idempotencyKey
    );

    /**
     * Retrieves one order belonging to the authenticated user.
     */
    OrderResponse getOrder(
            UUID orderId
    );

    /**
     * Retrieves the authenticated user's order history.
     */
    OrderPageResponse getOrders(
            int page,
            int size
    );

    /**
     * Cancels an order belonging to the authenticated user.
     */
    OrderResponse cancelOrder(
            UUID orderId,
            CancelOrderRequest request
    );
}