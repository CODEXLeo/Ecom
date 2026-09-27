package com.microservice.orders.idempotency.entity;

/**
 * Lifecycle state of an idempotency record.
 */
public enum IdempotencyRecordStatus {

    /**
     * The request has claimed the idempotency key and is currently
     * being processed.
     */
    IN_PROGRESS,

    /**
     * The request has completed and its HTTP response has been stored.
     */
    COMPLETED
}