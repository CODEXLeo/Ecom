package com.microservice.orders.exception;

/**
 * Indicates that the same idempotency key is currently being processed.
 */
public class IdempotencyInProgressException
        extends RuntimeException {

    public IdempotencyInProgressException(
            String message
    ) {
        super(message);
    }
}