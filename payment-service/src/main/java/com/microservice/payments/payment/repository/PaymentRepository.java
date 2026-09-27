package com.microservice.payments.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.microservice.payments.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import org.springframework.data.repository.query.Param;

public interface PaymentRepository
        extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentIdAndUserId(
            UUID paymentId,
            UUID userId
    );

    Optional<Payment> findByOrderId(
            UUID orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.paymentId = :paymentId
              and p.userId = :userId
            """)
    Optional<Payment> findByPaymentIdAndUserIdForUpdate(
            @Param("paymentId")
            UUID paymentId,

            @Param("userId")
            UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.paymentId = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId")
            UUID PaymentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.orderId = :orderId
            """)
    Optional<Payment> findByOrderIdForUpdate(
            @Param("orderId")
            UUID orderId
    );
}