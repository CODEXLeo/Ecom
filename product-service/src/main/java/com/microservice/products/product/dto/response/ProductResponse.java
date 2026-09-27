package com.microservice.products.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.microservice.products.product.entity.ProductStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProductResponse(

        @Schema(
                description = "Unique product identifier",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID productId,

        @Schema(
                description = "Product name",
                example = "Mechanical Keyboard"
        )
        String name,

        @Schema(
                description = "Product description",
                example = "RGB mechanical keyboard"
        )
        String description,

        @Schema(
                description = "Product price",
                example = "4999.00"
        )
        BigDecimal price,

        @Schema(
                description = "ISO 4217 currency code",
                example = "INR"
        )
        String currency,

        @Schema(
                description = "Current available stock",
                example = "100"
        )
        Integer stockQuantity,

        @Schema(
                description = "Current product status",
                example = "ACTIVE"
        )
        ProductStatus status,

        @Schema(
                description = "Product creation timestamp",
                example = "2026-09-10T12:00:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Last update timestamp",
                example = "2026-09-10T12:30:00Z"
        )
        Instant updatedAt

) {
}