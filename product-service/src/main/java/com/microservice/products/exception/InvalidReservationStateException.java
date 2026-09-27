package com.microservice.products.exception;

public class InvalidReservationStateException
        extends RuntimeException {

    public InvalidReservationStateException(String message) {
        super(message);
    }
}