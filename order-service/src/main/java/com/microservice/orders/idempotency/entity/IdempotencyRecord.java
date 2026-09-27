package com.microservice.orders.idempotency.entity;

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

/**
 * Persistent idempotency record for client requests.
 *
 * One record exists for a particular:
 *
 *     userId + idempotencyKey
 *
 * combination.
 *
 * The request hash prevents a client from accidentally reusing the same
 * idempotency key for a different request.
 *
 * The response is persisted so a retried request can receive exactly the
 * previously generated response without executing the order operation again.
 */
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_user_key",
                        columnNames = {
                                "user_id",
                                "idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_idempotency_status_expiry",
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

    /**
     * Authenticated user who owns this idempotency key.
     */
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /**
     * Client supplied idempotency key.
     */
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 128
    )
    private String idempotencyKey;

    /**
     * SHA-256 hash of the normalized request representation.
     */
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

    /**
     * HTTP status of the completed response.
     *
     * Null while IN_PROGRESS.
     */
    @Column(name = "response_status")
    private Integer responseStatus;

    /**
     * Serialized response body.
     *
     * Null while IN_PROGRESS.
     */
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

    /**
     * An IN_PROGRESS record older than this timestamp is considered stale.
     *
     * This allows recovery after a process crash.
     */
    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    /**
     * Optimistic locking for administrative/update operations.
     */
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
        this.userId = Objects.requireNonNull(userId);

        this.idempotencyKey =
                Objects.requireNonNull(idempotencyKey);

        this.requestHash =
                Objects.requireNonNull(requestHash);

        this.status =
                IdempotencyRecordStatus.IN_PROGRESS;

        this.responseStatus = null;
        this.responseBody = null;

        Instant now = Instant.now();

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

    /**
     * Reclaims an expired record for a new attempt using the same key.
     *
     * The caller must hold the database lock for this record.
     */
    public void reclaim(
            String requestHash,
            Instant expiresAt
    ) {
        if (!isExpired()) {
            throw new IllegalStateException(
                    "Only an expired idempotency record can be reclaimed"
            );
        }

        this.requestHash =
                Objects.requireNonNull(requestHash);

        this.status =
                IdempotencyRecordStatus.IN_PROGRESS;

        this.responseStatus = null;
        this.responseBody = null;

        this.expiresAt =
                Objects.requireNonNull(expiresAt);

        touch();
    }

    /**
     * Stores the final HTTP response.
     *
     * The record becomes immutable from an idempotency perspective after
     * completion.
     */
    public void complete(
            int responseStatus,
            String responseBody
    ) {
        if (status != IdempotencyRecordStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Only an in-progress idempotency record can be completed"
            );
        }

        if (responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException(
                    "Invalid HTTP response status: "
                            + responseStatus
            );
        }

        this.responseStatus = responseStatus;
        this.responseBody = responseBody;

        this.status =
                IdempotencyRecordStatus.COMPLETED;

        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isCompleted() {
        return status == IdempotencyRecordStatus.COMPLETED;
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