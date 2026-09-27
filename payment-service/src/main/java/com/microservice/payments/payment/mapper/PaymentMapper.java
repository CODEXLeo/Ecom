package com.microservice.payments.payment.mapper;

import com.microservice.payments.payment.dto.response.PaymentResponse;
import com.microservice.payments.payment.entity.Payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {

        return new PaymentResponse(

                payment.getPaymentId(),

                payment.getOrderId(),

                payment.getUserId(),

                payment.getPaymentMethod(),

                payment.getStatus(),

                payment.getAmount(),

                payment.getCurrency(),

                payment.getProviderPaymentId(),

                payment.getFailureCode(),

                payment.getFailureMessage(),

                payment.getCreatedAt(),

                payment.getUpdatedAt(),

                payment.getAuthorizedAt(),

                payment.getCapturedAt(),

                payment.getVoidedAt(),

                payment.getRefundedAt()
        );
    }
}