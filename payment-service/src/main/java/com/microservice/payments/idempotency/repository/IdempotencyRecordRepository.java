package com.microservice.payments.idempotency.repository;

import java.util.Optional;
import java.util.UUID;

import com.microservice.payments.idempotency.entity.IdempotencyRecord;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord>
    findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from IdempotencyRecord r
            where r.userId = :userId
              and r.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord>
    findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId")
            UUID userId,

            @Param("idempotencyKey")
            String idempotencyKey
    );
}