package com.microservice.products.exception;

public class InsufficientStockException
        extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}