package com.microservice.orders.inventory.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(

        UUID reservationId,

        UUID orderId,

        UUID productId,

        Integer quantity,

        String status,

        Instant createdAt,

        Instant expiresAt,

        Instant updatedAt

) {
}