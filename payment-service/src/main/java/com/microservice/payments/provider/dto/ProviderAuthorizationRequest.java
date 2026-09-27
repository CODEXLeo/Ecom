package com.microservice.payments.provider.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.microservice.payments.payment.entity.PaymentMethod;

public record ProviderAuthorizationRequest(

        UUID paymentId,

        UUID orderId,

        BigDecimal amount,

        String currency,

        PaymentMethod paymentMethod
) {
}