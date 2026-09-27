package com.microservice.orders.integration.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.microservice.orders.order.entity.PaymentMethod;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        UUID userId,
        PaymentMethod paymentMethod,
        String status,
        BigDecimal amount,
        String currency,
        String providerPaymentId,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant authorizedAt,
        Instant capturedAt,
        Instant voidedAt,
        Instant refundedAt
) {
}