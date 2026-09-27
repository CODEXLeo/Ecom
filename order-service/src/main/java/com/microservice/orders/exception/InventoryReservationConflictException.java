package com.microservice.orders.exception;

public class InventoryReservationConflictException
        extends RuntimeException {

    public InventoryReservationConflictException(
            String message
    ) {
        super(message);
    }
}