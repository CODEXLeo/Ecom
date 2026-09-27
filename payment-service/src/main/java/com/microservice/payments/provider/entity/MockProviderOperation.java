package com.microservice.payments.provider.entity;

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

@Entity
@Table(
        name = "mock_provider_operations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mock_provider_operation_key",
                        columnNames = {
                                "operation_type",
                                "idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_mock_provider_operation_payment",
                        columnList = "provider_payment_id"
                )
        }
)
public class MockProviderOperation {

    @Id
    @Column(
            name = "operation_id",
            nullable = false,
            updatable = false
    )
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "operation_type",
            nullable = false,
            length = 30
    )
    private MockProviderOperationType operationType;

    /*
     * Provider-side idempotency key.
     *
     * This is intentionally separate from the Payment Service's
     * own idempotency record.
     */
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 200
    )
    private String idempotencyKey;

    /*
     * Hash of the complete provider request.
     *
     * Prevents the same idempotency key from being reused
     * with different request data.
     */
    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    /*
     * Nullable because an unsuccessful authorization may not
     * create a provider payment.
     */
    @Column(
            name = "provider_payment_id",
            length = 150
    )
    private String providerPaymentId;

    @Column(
            name = "successful",
            nullable = false
    )
    private boolean successful;

    @Column(
            name = "failure_code",
            length = 100
    )
    private String failureCode;

    @Column(
            name = "failure_message",
            length = 500
    )
    private String failureMessage;

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

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected MockProviderOperation() {
        // JPA
    }

    private MockProviderOperation(
            MockProviderOperationType operationType,
            String idempotencyKey,
            String requestHash,
            String providerPaymentId,
            boolean successful,
            String failureCode,
            String failureMessage
    ) {

        this.operationId =
                UUID.randomUUID();

        this.operationType =
                Objects.requireNonNull(
                        operationType,
                        "operationType must not be null"
                );

        this.idempotencyKey =
                Objects.requireNonNull(
                        idempotencyKey,
                        "idempotencyKey must not be null"
                );

        this.requestHash =
                Objects.requireNonNull(
                        requestHash,
                        "requestHash must not be null"
                );

        this.providerPaymentId =
                providerPaymentId;

        this.successful =
                successful;

        this.failureCode =
                failureCode;

        this.failureMessage =
                failureMessage;

        Instant now =
                Instant.now();

        this.createdAt =
                now;

        this.updatedAt =
                now;
    }

    public static MockProviderOperation success(
            MockProviderOperationType operationType,
            String idempotencyKey,
            String requestHash,
            String providerPaymentId
    ) {

        return new MockProviderOperation(
                operationType,
                idempotencyKey,
                requestHash,
                providerPaymentId,
                true,
                null,
                null
        );
    }

    public static MockProviderOperation failure(
            MockProviderOperationType operationType,
            String idempotencyKey,
            String requestHash,
            String providerPaymentId,
            String failureCode,
            String failureMessage
    ) {

        return new MockProviderOperation(
                operationType,
                idempotencyKey,
                requestHash,
                providerPaymentId,
                false,
                failureCode,
                failureMessage
        );
    }

    public UUID getOperationId() {
        return operationId;
    }

    public MockProviderOperationType getOperationType() {
        return operationType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}