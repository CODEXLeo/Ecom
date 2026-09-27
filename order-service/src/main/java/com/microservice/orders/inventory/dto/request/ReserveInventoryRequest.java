package com.microservice.orders.inventory.dto.request;

import java.util.UUID;

public record ReserveInventoryRequest(

        UUID orderId,

        UUID productId,

        Integer quantity

) {
}