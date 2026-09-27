package com.microservice.products.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(

        @Schema(
                description = "New product name",
                example = "Mechanical Keyboard Pro"
        )
        @Size(
                max = 150,
                message =
                        "Product name must not exceed 150 characters"
        )
        String name,

        @Schema(
                description = "New product description",
                example = "Updated mechanical keyboard description"
        )
        @Size(
                max = 2000,
                message =
                        "Description must not exceed 2000 characters"
        )
        String description,

        @Schema(
                description = "New product price",
                example = "5499.00"
        )
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
                description = "New ISO 4217 currency code",
                example = "INR"
        )
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message =
                        "Currency must be a 3-letter uppercase ISO code"
        )
        String currency

) {
}