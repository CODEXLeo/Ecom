package com.microservice.payments.idempotency.service;

import java.util.UUID;

public interface IdempotencyService {

    IdempotencyResult begin(
            UUID userId,
            String idempotencyKey,
            String requestHash
    );

    void complete(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    );

    void completeFailure(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    );
}