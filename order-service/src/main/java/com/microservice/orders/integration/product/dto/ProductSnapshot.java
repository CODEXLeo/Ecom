package com.microservice.orders.integration.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(

        UUID productId,

        String productName,

        BigDecimal unitPrice,

        String currency

) {
}