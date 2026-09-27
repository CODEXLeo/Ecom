package com.microservice.orders.integration.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.microservice.orders.order.entity.PaymentMethod;

public record PaymentRequest(
        UUID orderId,
        UUID userId,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        String currency
) {
}