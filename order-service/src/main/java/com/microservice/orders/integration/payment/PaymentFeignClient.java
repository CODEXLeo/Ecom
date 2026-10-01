package com.microservice.orders.integration.payment;

import java.util.UUID;

import com.microservice.orders.integration.payment.config.PaymentFeignConfiguration;
import com.microservice.orders.integration.payment.dto.PaymentRequest;
import com.microservice.orders.integration.payment.dto.PaymentResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", configuration = PaymentFeignConfiguration.class)
public interface PaymentFeignClient {

    @PostMapping("/api/v1/payments/internal/authorize")
    PaymentResponse authorize(
            @RequestBody
            PaymentRequest request,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    );

    @PostMapping("/api/v1/payments/internal/{paymentId}/{operation}")
    PaymentResponse operate(
            @PathVariable("paymentId")
            UUID paymentId,

            @PathVariable("operation")
            String operation,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    );
}