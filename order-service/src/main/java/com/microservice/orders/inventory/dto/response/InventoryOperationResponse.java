package com.microservice.orders.inventory.dto.response;

import java.util.UUID;

public record InventoryOperationResponse(

        UUID reservationId,

        UUID orderId,

        UUID productId,

        Integer quantity,

        String status

) {
}