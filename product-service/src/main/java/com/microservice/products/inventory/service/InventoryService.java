package com.microservice.products.inventory.service;

import java.util.UUID;

import com.microservice.products.inventory.dto.request.ReservationRequest;
import com.microservice.products.inventory.dto.response.ReservationResponse;

public interface InventoryService {

    ReservationResponse reserve(
            ReservationRequest request,
            String idempotencyKey
    );

    ReservationResponse commit(
            UUID reservationId
    );

    ReservationResponse release(
            UUID reservationId
    );

    int expireReservations();
}