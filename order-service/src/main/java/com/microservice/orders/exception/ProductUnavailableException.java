package com.microservice.orders.exception;

public class ProductUnavailableException
        extends RuntimeException {

    public ProductUnavailableException(
            String message
    ) {
        super(message);
    }
}