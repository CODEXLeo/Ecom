package com.microservice.orders.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        int maxItemsPerOrder,
        int maxQuantityPerItem,
        int idempotencyKeyMaxLength
) {
}