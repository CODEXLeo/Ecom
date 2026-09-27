package com.microservice.orders.idempotency.service;

import java.util.UUID;

/**
 * Coordinates persistent idempotency for externally initiated commands.
 */
public interface IdempotencyService {

    /**
     * Attempts to claim an idempotency key for processing.
     *
     * Behaviour:
     *
     * 1. No existing record:
     *      create IN_PROGRESS record
     *      return shouldProcess=true
     *
     * 2. Existing COMPLETED record with same request hash:
     *      return stored response
     *
     * 3. Existing record with different request hash:
     *      reject request
     *
     * 4. Existing non-expired IN_PROGRESS record:
     *      reject duplicate concurrent request
     *
     * 5. Expired IN_PROGRESS record with same request hash:
     *      reclaim it
     */
    IdempotencyResult begin(
            UUID userId,
            String idempotencyKey,
            String requestHash
    );

    /**
     * Persists the final response for a successfully completed command.
     *
     * This method runs in its own transaction so the idempotency response
     * is committed independently of the surrounding request transaction.
     */
    void complete(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    );
}