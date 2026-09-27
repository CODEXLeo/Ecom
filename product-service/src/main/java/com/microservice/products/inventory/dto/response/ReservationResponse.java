package com.microservice.products.inventory.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.microservice.products.inventory.entity.ReservationStatus;

public record ReservationResponse(
        UUID reservationId,
        UUID orderId,
        UUID productId,
        Integer quantity,
        ReservationStatus status,
        Instant expiresAt
) {
}