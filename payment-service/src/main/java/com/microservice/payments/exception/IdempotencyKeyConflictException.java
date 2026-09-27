package com.microservice.payments.exception;

public class IdempotencyKeyConflictException
        extends RuntimeException {

    public IdempotencyKeyConflictException(
            String message
    ) {

        super(message);
    }
}