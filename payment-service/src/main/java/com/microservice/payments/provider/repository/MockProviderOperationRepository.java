package com.microservice.payments.provider.repository;

import java.util.Optional;
import java.util.UUID;

import com.microservice.payments.provider.entity.MockProviderOperation;
import com.microservice.payments.provider.entity.MockProviderOperationType;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface MockProviderOperationRepository
        extends JpaRepository<
                MockProviderOperation,
                UUID
        > {

    Optional<MockProviderOperation>
    findByOperationTypeAndIdempotencyKey(
            MockProviderOperationType operationType,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from MockProviderOperation o
            where o.operationType = :operationType
              and o.idempotencyKey = :idempotencyKey
            """)
    Optional<MockProviderOperation>
    findByOperationTypeAndIdempotencyKeyForUpdate(
            @Param("operationType")
            MockProviderOperationType operationType,

            @Param("idempotencyKey")
            String idempotencyKey
    );
}