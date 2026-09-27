package com.microservice.payments.payment.service;

import java.util.UUID;

import com.microservice.payments.payment.dto.request.AuthorizePaymentRequest;
import com.microservice.payments.payment.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse authorize(
            AuthorizePaymentRequest request,
            String idempotencyKey
    );

    PaymentResponse getPayment(
            UUID paymentId
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