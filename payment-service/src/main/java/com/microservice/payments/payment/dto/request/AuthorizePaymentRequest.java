package com.microservice.payments.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.microservice.payments.payment.entity.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AuthorizePaymentRequest(

        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than zero"
        )
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must be a 3-letter ISO currency code"
        )
        String currency
) {
}