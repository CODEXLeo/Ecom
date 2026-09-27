package com.microservice.products.exception;

public class ProductConflictException extends RuntimeException {

    public ProductConflictException(String message) {
        super(message);
    }
}