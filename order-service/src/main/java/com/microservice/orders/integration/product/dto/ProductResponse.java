package com.microservice.orders.integration.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(

        UUID productId,

        String name,

        String description,

        BigDecimal price,

        String currency,

        Integer stockQuantity,

        String status,

        Instant createdAt,

        Instant updatedAt

) {
}