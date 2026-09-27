package com.microservice.orders.inventory;

import java.util.UUID;

import com.microservice.orders.inventory.dto.response.InventoryOperationResponse;
import com.microservice.orders.inventory.dto.response.ReservationResponse;

public interface InventoryClient {

    ReservationResponse reserve(
            UUID orderId,
            UUID productId,
            int quantity,
            String idempotencyKey
    );

    InventoryOperationResponse commit(
            UUID reservationId
    );

    InventoryOperationResponse release(
            UUID reservationId
    );
}