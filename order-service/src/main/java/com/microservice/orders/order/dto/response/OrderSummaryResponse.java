package com.microservice.orders.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.microservice.orders.order.entity.OrderStatus;
import com.microservice.orders.order.entity.PaymentMethod;
import com.microservice.orders.order.entity.PaymentStatus;

public record OrderSummaryResponse(

        UUID orderId,

        OrderStatus status,

        PaymentMethod paymentMethod,

        PaymentStatus paymentStatus,

        BigDecimal totalAmount,

        String currency,

        Instant createdAt

) {
}