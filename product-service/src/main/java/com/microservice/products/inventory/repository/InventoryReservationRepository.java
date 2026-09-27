package com.microservice.products.inventory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.microservice.products.inventory.entity.InventoryReservation;
import com.microservice.products.inventory.entity.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByIdempotencyKey(
            String idempotencyKey
    );

    List<InventoryReservation> findByStatusAndExpiresAtBefore(
            ReservationStatus status,
            Instant instant
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select r
           from InventoryReservation r
           where r.reservationId = :reservationId
           """)
    Optional<InventoryReservation> findByIdForUpdate(
            @Param("reservationId") UUID reservationId
    );
}