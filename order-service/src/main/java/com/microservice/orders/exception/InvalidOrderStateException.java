package com.microservice.orders.exception;

public class InvalidOrderStateException
        extends RuntimeException {

    public InvalidOrderStateException(
            String message
    ) {
        super(message);
    }
}