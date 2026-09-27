package com.microservice.payments.idempotency.service;

public record IdempotencyResult(
        boolean shouldProcess,
        Integer responseStatus,
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

    public boolean isReplay() {

        return !shouldProcess;
    }

    public boolean isSuccessfulReplay() {

        return isReplay()
                && responseStatus != null
                && responseStatus < 400;
    }

    public boolean isFailedReplay() {

        return isReplay()
                && responseStatus != null
                && responseStatus >= 400;
    }
}