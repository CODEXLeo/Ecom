package com.microservice.orders.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(

        UUID orderItemId,

        UUID productId,

        String productName,

        BigDecimal unitPrice,

        String currency,

        Integer quantity,

        BigDecimal lineTotal

) {
}