package com.microservice.payments.idempotency.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.microservice.payments.config.properties.PaymentProperties;
import com.microservice.payments.exception.IdempotencyInProgressException;
import com.microservice.payments.exception.IdempotencyKeyConflictException;
import com.microservice.payments.idempotency.entity.IdempotencyRecord;
import com.microservice.payments.idempotency.repository.IdempotencyRecordRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyServiceImpl
        implements IdempotencyService {

    private final IdempotencyRecordRepository repository;

    private final PaymentProperties properties;

    private final TransactionTemplate requiresNewTransaction;

    public IdempotencyServiceImpl(
            IdempotencyRecordRepository repository,
            PaymentProperties properties,
            PlatformTransactionManager transactionManager
    ) {

        this.repository =
                repository;

        this.properties =
                properties;

        this.requiresNewTransaction =
                new TransactionTemplate(
                        transactionManager
                );

        this.requiresNewTransaction
                .setPropagationBehavior(
                        TransactionDefinition
                                .PROPAGATION_REQUIRES_NEW
                );
    }

    // ================================================================
    // BEGIN
    // ================================================================

    @Override
    public IdempotencyResult begin(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {

        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        String normalizedKey =
                validateAndNormalizeKey(
                        idempotencyKey
                );

        validateHash(
                requestHash
        );

        try {

            return requiresNewTransaction
                    .execute(
                            status ->
                                    beginInsideTransaction(
                                            userId,
                                            normalizedKey,
                                            requestHash
                                    )
                    );

        } catch (DataIntegrityViolationException exception) {

            return resolveExistingRecord(
                    userId,
                    normalizedKey,
                    requestHash
            );
        }
    }

    // ================================================================
    // BEGIN INSIDE TRANSACTION
    // ================================================================

    private IdempotencyResult beginInsideTransaction(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {

        Instant now =
                Instant.now();

        var existing =
                repository
                        .findByUserIdAndIdempotencyKeyForUpdate(
                                userId,
                                idempotencyKey
                        );

        if (existing.isEmpty()) {

            repository.saveAndFlush(
                    IdempotencyRecord.create(
                            userId,
                            idempotencyKey,
                            requestHash,
                            now.plus(
                                    properties
                                            .idempotencyRecordTtl()
                            )
                    )
            );

            return IdempotencyResult.process();
        }

        return resolveRecord(
                existing.get(),
                requestHash,
                now
        );
    }

    // ================================================================
    // CONCURRENT INSERT RECOVERY
    // ================================================================

    private IdempotencyResult resolveExistingRecord(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {

        var existing =
                repository
                        .findByUserIdAndIdempotencyKey(
                                userId,
                                idempotencyKey
                        );

        if (existing.isEmpty()) {

            throw new IdempotencyKeyConflictException(
                    "Unable to establish idempotency state; retry"
            );
        }

        return resolveRecord(
                existing.get(),
                requestHash,
                Instant.now()
        );
    }

    // ================================================================
    // RESOLVE RECORD
    // ================================================================

    private IdempotencyResult resolveRecord(
            IdempotencyRecord record,
            String requestHash,
            Instant now
    ) {

        if (!record.getRequestHash()
                .equals(requestHash)) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used "
                            + "with a different request"
            );
        }

        if (record.isCompleted()) {

            if (record.getResponseStatus() == null) {

                throw new IllegalStateException(
                        "Completed idempotency record "
                                + "has no response status"
                );
            }

            if (record.getResponseBody() == null
                    || record.getResponseBody().isBlank()) {

                throw new IllegalStateException(
                        "Completed idempotency record "
                                + "has no response body"
                );
            }

            return IdempotencyResult.replay(
                    record.getResponseStatus(),
                    record.getResponseBody()
            );
        }

        if (!record.isExpired()) {

            throw new IdempotencyInProgressException(
                    "A request with this Idempotency-Key "
                            + "is already being processed"
            );
        }

        /*
         * The previous execution exceeded its TTL.
         *
         * Reclaim the record so the deterministic payment ID
         * and provider operation key can be reused safely.
         */
        record.reclaim(
                requestHash,
                now.plus(
                        properties
                                .idempotencyRecordTtl()
                )
        );

        repository.saveAndFlush(
                record
        );

        return IdempotencyResult.process();
    }

    // ================================================================
    // COMPLETE SUCCESS
    // ================================================================

    @Override
    public void complete(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    ) {

        if (responseStatus < 200
                || responseStatus >= 400) {

            throw new IllegalArgumentException(
                    "Success response status must be "
                            + "between 200 and 399"
            );
        }

        completeInternal(
                userId,
                idempotencyKey,
                requestHash,
                responseStatus,
                responseBody
        );
    }

    // ================================================================
    // COMPLETE FAILURE
    // ================================================================

    @Override
    public void completeFailure(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    ) {

        if (responseStatus < 400) {

            throw new IllegalArgumentException(
                    "Failure response status must be >= 400"
            );
        }

        completeInternal(
                userId,
                idempotencyKey,
                requestHash,
                responseStatus,
                responseBody
        );
    }

    // ================================================================
    // COMPLETE INTERNAL
    // ================================================================

    private void completeInternal(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody
    ) {

        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        Objects.requireNonNull(
                responseBody,
                "responseBody must not be null"
        );

        if (responseBody.isBlank()) {

            throw new IllegalArgumentException(
                    "responseBody must not be blank"
            );
        }

        String normalizedKey =
                validateAndNormalizeKey(
                        idempotencyKey
                );

        validateHash(
                requestHash
        );

        requiresNewTransaction
                .executeWithoutResult(status -> {

                    IdempotencyRecord record =
                            repository
                                    .findByUserIdAndIdempotencyKeyForUpdate(
                                            userId,
                                            normalizedKey
                                    )
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "Idempotency record not found: "
                                                                    + normalizedKey
                                                    )
                                    );

                    if (!record.getRequestHash()
                            .equals(requestHash)) {

                        throw new IdempotencyKeyConflictException(
                                "Idempotency request hash "
                                        + "does not match original request"
                        );
                    }

                    /*
                     * Once a record is completed, never overwrite it.
                     *
                     * This guarantees that the first terminal result
                     * remains authoritative for the idempotency key.
                     */
                    if (record.isCompleted()) {
                        return;
                    }

                    record.complete(
                            responseStatus,
                            responseBody
                    );

                    repository.saveAndFlush(
                            record
                    );
                });
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    private String validateAndNormalizeKey(
            String idempotencyKey
    ) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must not be blank"
            );
        }

        String normalized =
                idempotencyKey.trim();

        if (normalized.length()
                > properties.idempotencyKeyMaxLength()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed "
                            + properties.idempotencyKeyMaxLength()
                            + " characters"
            );
        }

        return normalized;
    }

    private static void validateHash(
            String requestHash
    ) {

        if (requestHash == null
                || !requestHash.matches(
                        "[a-fA-F0-9]{64}"
                )) {

            throw new IllegalArgumentException(
                    "Request hash must be a SHA-256 hexadecimal value"
            );
        }
    }
}