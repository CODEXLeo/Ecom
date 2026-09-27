package com.microservice.products.exception;

import java.util.UUID;

public class ReservationNotFoundException
        extends RuntimeException {

    public ReservationNotFoundException(UUID reservationId) {
        super("Inventory reservation not found: " + reservationId);
    }
}