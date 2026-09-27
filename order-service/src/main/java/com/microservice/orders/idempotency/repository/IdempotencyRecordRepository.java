package com.microservice.orders.idempotency.repository;

import java.util.Optional;
import java.util.UUID;

import com.microservice.orders.idempotency.entity.IdempotencyRecord;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for idempotency records.
 */
public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Normal lookup.
     */
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    /**
     * Pessimistic lock used when claiming/replaying an existing key.
     *
     * This serializes concurrent requests using the same:
     *
     *     user + idempotency key
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from IdempotencyRecord r
            where r.userId = :userId
              and r.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}