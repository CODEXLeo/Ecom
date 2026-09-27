package com.microservice.products.inventory.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationRequest(

        @NotNull(message = "Order ID is required")
        @Schema(description = "Order being checked out")
        UUID orderId,

        @NotNull(message = "Product ID is required")
        @Schema(description = "Product to reserve")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        @Schema(description = "Quantity to reserve", example = "1", minimum = "1")
        Integer quantity
) {
}