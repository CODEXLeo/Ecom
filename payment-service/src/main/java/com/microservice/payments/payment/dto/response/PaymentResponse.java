package com.microservice.payments.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.microservice.payments.payment.entity.PaymentMethod;
import com.microservice.payments.payment.entity.PaymentStatus;

public record PaymentResponse(

        UUID paymentId,

        UUID orderId,

        UUID userId,

        PaymentMethod paymentMethod,

        PaymentStatus status,

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