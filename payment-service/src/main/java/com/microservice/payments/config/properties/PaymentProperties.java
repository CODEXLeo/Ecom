package com.microservice.payments.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(

        boolean providerMockEnabled,

        Duration idempotencyRecordTtl,

        int idempotencyKeyMaxLength
) {
}