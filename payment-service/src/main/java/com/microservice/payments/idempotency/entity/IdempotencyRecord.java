package com.microservice.payments.idempotency.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_idempotency_user_key",
                        columnNames = {
                                "user_id",
                                "idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_payment_idempotency_status_expiry",
                        columnList = "status, expires_at"
                )
        }
)
public class IdempotencyRecord {

    @Id
    @UuidGenerator
    @Column(
            name = "idempotency_record_id",
            nullable = false,
            updatable = false
    )
    private UUID idempotencyRecordId;

    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 128
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private IdempotencyRecordStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(
            name = "response_body",
            columnDefinition = "LONGTEXT"
    )
    private String responseBody;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected IdempotencyRecord() {
        // JPA
    }

    private IdempotencyRecord(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            Instant expiresAt
    ) {
        this.userId =
                Objects.requireNonNull(userId);

        this.idempotencyKey =
                Objects.requireNonNull(idempotencyKey);

        this.requestHash =
                Objects.requireNonNull(requestHash);

        this.status =
                IdempotencyRecordStatus.IN_PROGRESS;

        Instant now =
                Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt =
                Objects.requireNonNull(expiresAt);
    }

    public static IdempotencyRecord create(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            Instant expiresAt
    ) {
        return new IdempotencyRecord(
                userId,
                idempotencyKey,
                requestHash,
                expiresAt
        );
    }

    public void reclaim(
            String requestHash,
            Instant expiresAt
    ) {
        this.requestHash =
                Objects.requireNonNull(requestHash);

        this.status =
                IdempotencyRecordStatus.IN_PROGRESS;

        this.responseStatus =
                null;

        this.responseBody =
                null;

        this.expiresAt =
                Objects.requireNonNull(expiresAt);

        touch();
    }

    public void complete(
            int responseStatus,
            String responseBody
    ) {
        this.responseStatus =
                responseStatus;

        this.responseBody =
                responseBody;

        this.status =
                IdempotencyRecordStatus.COMPLETED;

        touch();
    }

    public boolean isExpired() {
        return Instant.now()
                .isAfter(expiresAt);
    }

    public boolean isCompleted() {
        return status ==
                IdempotencyRecordStatus.COMPLETED;
    }

    private void touch() {
        this.updatedAt =
                Instant.now();
    }

    public UUID getIdempotencyRecordId() {
        return idempotencyRecordId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyRecordStatus getStatus() {
        return status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getVersion() {
        return version;
    }
}