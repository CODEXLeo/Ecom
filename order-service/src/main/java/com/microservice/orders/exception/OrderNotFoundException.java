package com.microservice.orders.exception;

public class OrderNotFoundException
        extends RuntimeException {

    public OrderNotFoundException(
            String message
    ) {
        super(message);
    }
}