package com.microservice.orders.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.microservice.orders.order.entity.OrderStatus;
import com.microservice.orders.order.entity.PaymentMethod;
import com.microservice.orders.order.entity.PaymentStatus;

public record OrderResponse(

        UUID orderId,

        UUID userId,

        UUID paymentId,

        OrderStatus status,

        PaymentMethod paymentMethod,

        PaymentStatus paymentStatus,

        BigDecimal subtotal,

        BigDecimal totalAmount,

        String currency,

        List<OrderItemResponse> items,

        Instant createdAt,

        Instant updatedAt,

        Instant cancelledAt,

        String cancellationReason
) {
}