package com.microservice.orders.idempotency.service;

/**
 * Result of attempting to claim an idempotency key.
 */
public record IdempotencyResult(

        /**
         * True when this request owns a newly claimed/reclaimed key
         * and should execute the business operation.
         */
        boolean shouldProcess,

        /**
         * Previously stored HTTP response status.
         *
         * Present when shouldProcess == false.
         */
        Integer responseStatus,

        /**
         * Previously stored response body.
         *
         * Present when shouldProcess == false.
         */
        String responseBody
) {

    public static IdempotencyResult process() {
        return new IdempotencyResult(
                true,
                null,
                null
        );
    }

    public static IdempotencyResult replay(
            int responseStatus,
            String responseBody
    ) {
        return new IdempotencyResult(
                false,
                responseStatus,
                responseBody
        );
    }
}