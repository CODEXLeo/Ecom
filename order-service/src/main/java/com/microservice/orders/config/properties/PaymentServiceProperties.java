package com.microservice.orders.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "payment.service"
)
public record PaymentServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}