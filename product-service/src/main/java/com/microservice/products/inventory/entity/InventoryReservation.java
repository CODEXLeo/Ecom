package com.microservice.products.inventory.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
        name = "inventory_reservations",
        indexes = {
                @Index(name = "idx_reservation_order", columnList = "order_id"),
                @Index(name = "idx_reservation_product", columnList = "product_id"),
                @Index(
                        name = "idx_reservation_status_expiry",
                        columnList = "status,expires_at"
                )
        }
)
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            updatable = false,
            length = 200
    )
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected InventoryReservation() {
    }

    public InventoryReservation(
            UUID orderId,
            UUID productId,
            Integer quantity,
            String idempotencyKey,
            Instant expiresAt) {

        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.RESERVED;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void commit() {
        this.status = ReservationStatus.COMMITTED;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
}