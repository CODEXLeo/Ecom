package com.microservice.orders.exception;

/**
 * Raised when an idempotency key is reused with a different request,
 * or when an existing request with that key is still being processed.
 */
public class IdempotencyKeyConflictException
        extends RuntimeException {

    public IdempotencyKeyConflictException(
            String message
    ) {
        super(message);
    }
}