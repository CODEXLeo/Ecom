package com.microservice.orders.integration.payment;

import java.util.UUID;

import com.microservice.orders.integration.payment.dto.PaymentRequest;
import com.microservice.orders.integration.payment.dto.PaymentResponse;

public interface PaymentClient {

    PaymentResponse authorize(
            PaymentRequest request,
            String idempotencyKey
    );

    PaymentResponse capture(
            UUID paymentId,
            String idempotencyKey
    );

    PaymentResponse voidPayment(
            UUID paymentId,
            String idempotencyKey
    );

    PaymentResponse refund(
            UUID paymentId,
            String idempotencyKey
    );
}