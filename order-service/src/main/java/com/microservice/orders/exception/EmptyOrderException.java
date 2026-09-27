package com.microservice.orders.exception;

public class EmptyOrderException
        extends RuntimeException {

    public EmptyOrderException(
            String message
    ) {
        super(message);
    }
}