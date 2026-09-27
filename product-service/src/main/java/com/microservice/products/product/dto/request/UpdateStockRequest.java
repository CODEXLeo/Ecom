package com.microservice.products.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateStockRequest(

        @Schema(
                description = "New absolute stock quantity",
                example = "150",
                minimum = "0"
        )
        @NotNull(
                message = "Stock quantity is required"
        )
        @PositiveOrZero(
                message = "Stock quantity cannot be negative"
        )
        Integer stockQuantity

) {
}