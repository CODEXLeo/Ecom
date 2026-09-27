package com.microservice.products.product.dto.request;

import com.microservice.products.product.entity.ProductStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(

        @Schema(
                description = "Product status",
                example = "ACTIVE"
        )
        @NotNull(
                message = "Product status is required"
        )
        ProductStatus status

) {
}