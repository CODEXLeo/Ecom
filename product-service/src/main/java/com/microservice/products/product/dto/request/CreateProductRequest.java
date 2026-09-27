package com.microservice.products.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(

        @Schema(
                description = "Product name",
                example = "Mechanical Keyboard"
        )
        @NotBlank(message = "Product name is required")
        @Size(
                max = 150,
                message = "Product name must not exceed 150 characters"
        )
        String name,

        @Schema(
                description = "Product description",
                example = "RGB mechanical keyboard with hot-swappable switches"
        )
        @Size(
                max = 2000,
                message = "Description must not exceed 2000 characters"
        )
        String description,

        @Schema(
                description = "Product price",
                example = "4999.00"
        )
        @NotNull(message = "Product price is required")
        @DecimalMin(
                value = "0.01",
                message = "Price must be greater than zero"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message =
                        "Price must have at most 17 integer digits "
                                + "and 2 decimal places"
        )
        BigDecimal price,

        @Schema(
                description = "ISO 4217 three-letter currency code",
                example = "INR"
        )
        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message =
                        "Currency must be a 3-letter uppercase ISO code"
        )
        String currency,

        @Schema(
                description = "Initial stock quantity",
                example = "100",
                minimum = "0"
        )
        @NotNull(message = "Stock quantity is required")
        @PositiveOrZero(
                message = "Stock quantity cannot be negative"
        )
        Integer stockQuantity

) {
}