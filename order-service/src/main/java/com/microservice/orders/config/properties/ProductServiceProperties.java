package com.microservice.orders.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.service")
public record ProductServiceProperties(

        String baseUrl,

        Duration connectTimeout,

        Duration readTimeout

) {
}