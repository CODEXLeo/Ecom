package com.microservice.payments.exception;

public class IdempotencyReplayException
        extends RuntimeException {

    private final int responseStatus;

    private final String responseBody;

    public IdempotencyReplayException(
            int responseStatus,
            String responseBody
    ) {

        super(
                "A previous request with this "
                        + "Idempotency-Key already completed "
                        + "with HTTP status "
                        + responseStatus
        );

        this.responseStatus =
                responseStatus;

        this.responseBody =
                responseBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}