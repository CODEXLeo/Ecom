package com.microservice.payments.provider.repository;

import java.util.Optional;

import com.microservice.payments.provider.entity.MockProviderPayment;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface MockProviderPaymentRepository
        extends JpaRepository<MockProviderPayment, String> {

    Optional<MockProviderPayment>
    findByPaymentId(
            java.util.UUID paymentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from MockProviderPayment p
            where p.providerPaymentId = :providerPaymentId
            """)
    Optional<MockProviderPayment>
    findByProviderPaymentIdForUpdate(
            @Param("providerPaymentId")
            String providerPaymentId
    );
}