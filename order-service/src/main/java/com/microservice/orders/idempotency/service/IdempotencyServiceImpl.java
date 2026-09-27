package com.microservice.orders.idempotency.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.microservice.orders.config.properties.IdempotencyProperties;
import com.microservice.orders.config.properties.OrderProperties;
import com.microservice.orders.exception.IdempotencyInProgressException;
import com.microservice.orders.exception.IdempotencyKeyConflictException;
import com.microservice.orders.idempotency.entity.IdempotencyRecord;
import com.microservice.orders.idempotency.repository.IdempotencyRecordRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistent idempotency implementation.
 *
 * The initial claim is committed in a REQUIRES_NEW transaction so that
 * the claim survives independently of the actual order transaction.
 */
@Service
public class IdempotencyServiceImpl
        implements IdempotencyService {

    private final IdempotencyRecordRepository repository;

    private final IdempotencyProperties idempotencyProperties;

    private final OrderProperties orderProperties;

    private final TransactionTemplate requiresNewTransaction;

    public IdempotencyServiceImpl(
            IdempotencyRecordRepository repository,
            PlatformTransactionManager transactionManager,
            IdempotencyProperties idempotencyProperties,
            OrderProperties orderProperties
    ) {
        this.repository = repository;
        this.idempotencyProperties = idempotencyProperties;
        this.orderProperties = orderProperties;

        this.requiresNewTransaction =
                new TransactionTemplate(
                        transactionManager
                );

        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

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
                validateAndNormalizeKey(idempotencyKey);

        validateHash(requestHash);

        /*
         * Concurrent callers may race while creating the first record.
         *
         * The database unique constraint is the final serialization
         * mechanism.
         */
        try {
            return requiresNewTransaction.execute(status ->
                    beginInTransaction(
                            userId,
                            normalizedKey,
                            requestHash
                    )
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * The competing transaction has either committed or failed.
             *
             * Inspect the committed record using another transaction.
             */
            return requiresNewTransaction.execute(status ->
                    resolveExistingRecord(
                            userId,
                            normalizedKey,
                            requestHash
                    )
            );
        }
    }

    private IdempotencyResult beginInTransaction(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        Instant now = Instant.now();

        var existing =
                repository.findByUserIdAndIdempotencyKeyForUpdate(
                        userId,
                        idempotencyKey
                );

        if (existing.isEmpty()) {

            IdempotencyRecord record =
                    IdempotencyRecord.create(
                            userId,
                            idempotencyKey,
                            requestHash,
                            now.plus(
                                    idempotencyProperties.recordTtl()
                            )
                    );

            repository.saveAndFlush(record);

            return IdempotencyResult.process();
        }

        return resolveRecord(
                existing.get(),
                requestHash,
                now
        );
    }

    private IdempotencyResult resolveExistingRecord(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        var existing =
                repository.findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );

        if (existing.isEmpty()) {

            throw new IdempotencyKeyConflictException(
                    "Unable to establish idempotency state; please retry"
            );
        }

        return resolveRecord(
                existing.get(),
                requestHash,
                Instant.now()
        );
    }

    private IdempotencyResult resolveRecord(
            IdempotencyRecord record,
            String requestHash,
            Instant now
    ) {
        /*
         * An idempotency key identifies one logical request.
         */
        if (!record.getRequestHash().equals(requestHash)) {

            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used with a different request"
            );
        }

        /*
         * Completed request:
         *
         * Never execute the business operation again.
         *
         * Return the original response.
         */
        if (record.isCompleted()) {

            if (record.getResponseStatus() == null) {

                throw new IllegalStateException(
                        "Completed idempotency record has no response status"
                );
            }

            return IdempotencyResult.replay(
                    record.getResponseStatus(),
                    record.getResponseBody()
            );
        }

        /*
         * Another request is currently processing the same key.
         */
        if (!record.isExpired()) {

            throw new IdempotencyInProgressException(
                    "A request with this Idempotency-Key is already being processed"
            );
        }

        /*
         * Previous request became stale.
         *
         * This handles process crashes and abandoned requests.
         */
        record.reclaim(
                requestHash,
                now.plus(
                        idempotencyProperties.recordTtl()
                )
        );

        repository.saveAndFlush(record);

        return IdempotencyResult.process();
    }

    @Override
    public void complete(
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

        String normalizedKey =
                validateAndNormalizeKey(idempotencyKey);

        validateHash(requestHash);

        requiresNewTransaction.executeWithoutResult(status -> {

            IdempotencyRecord record =
                    repository
                            .findByUserIdAndIdempotencyKeyForUpdate(
                                    userId,
                                    normalizedKey
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Idempotency record not found for key: "
                                                    + normalizedKey
                                    )
                            );

            if (!record.getRequestHash().equals(requestHash)) {

                throw new IdempotencyKeyConflictException(
                        "Idempotency request hash does not match the original request"
                );
            }

            /*
             * Do not overwrite an already completed response.
             */
            if (record.isCompleted()) {
                return;
            }

            record.complete(
                    responseStatus,
                    responseBody
            );

            repository.saveAndFlush(record);
        });
    }

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
                > orderProperties.idempotencyKeyMaxLength()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed "
                            + orderProperties.idempotencyKeyMaxLength()
                            + " characters"
            );
        }

        return normalized;
    }

    private static void validateHash(
            String requestHash
    ) {
        if (requestHash == null
                || requestHash.isBlank()) {

            throw new IllegalArgumentException(
                    "Request hash must not be blank"
            );
        }

        if (!requestHash.matches("[a-fA-F0-9]{64}")) {

            throw new IllegalArgumentException(
                    "Request hash must be a SHA-256 hexadecimal value"
            );
        }
    }
}