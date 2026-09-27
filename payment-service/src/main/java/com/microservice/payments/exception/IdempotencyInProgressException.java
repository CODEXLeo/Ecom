package com.microservice.payments.exception;

public class IdempotencyInProgressException
        extends RuntimeException {

    public IdempotencyInProgressException(
            String message
    ) {

        super(message);
    }
}