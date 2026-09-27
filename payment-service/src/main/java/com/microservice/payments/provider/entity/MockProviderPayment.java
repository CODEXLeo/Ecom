package com.microservice.payments.provider.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.microservice.payments.payment.entity.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "mock_provider_payments",
        indexes = {
                @Index(
                        name = "idx_mock_provider_payment_order",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_mock_provider_payment_status",
                        columnList = "status"
                )
        }
)
public class MockProviderPayment {

    @Id
    @Column(
            name = "provider_payment_id",
            nullable = false,
            updatable = false,
            length = 150
    )
    private String providerPaymentId;

    @Column(
            name = "payment_id",
            nullable = false,
            updatable = false
    )
    private UUID paymentId;

    @Column(
            name = "order_id",
            nullable = false,
            updatable = false
    )
    private UUID orderId;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Column(
            name = "currency",
            nullable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 30
    )
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private MockProviderPaymentStatus status;

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

    protected MockProviderPayment() {
        // JPA
    }

    private MockProviderPayment(
            String providerPaymentId,
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod
    ) {
        this.providerPaymentId =
                Objects.requireNonNull(
                        providerPaymentId
                );

        this.paymentId =
                Objects.requireNonNull(
                        paymentId
                );

        this.orderId =
                Objects.requireNonNull(
                        orderId
                );

        this.amount =
                Objects.requireNonNull(
                        amount
                );

        this.currency =
                normalizeCurrency(
                        currency
                );

        this.paymentMethod =
                Objects.requireNonNull(
                        paymentMethod
                );

        this.status =
                MockProviderPaymentStatus.AUTHORIZED;

        Instant now =
                Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    public static MockProviderPayment authorize(
            String providerPaymentId,
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod
    ) {
        return new MockProviderPayment(
                providerPaymentId,
                paymentId,
                orderId,
                amount,
                currency,
                paymentMethod
        );
    }

    public void capture() {
        requireStatus(
                MockProviderPaymentStatus.AUTHORIZED
        );

        this.status =
                MockProviderPaymentStatus.CAPTURED;

        touch();
    }

    public void voidPayment() {
        requireStatus(
                MockProviderPaymentStatus.AUTHORIZED
        );

        this.status =
                MockProviderPaymentStatus.VOIDED;

        touch();
    }

    public void refund() {
        requireStatus(
                MockProviderPaymentStatus.CAPTURED
        );

        this.status =
                MockProviderPaymentStatus.REFUNDED;

        touch();
    }

    private void requireStatus(
            MockProviderPaymentStatus expected
    ) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Mock provider payment must be "
                            + expected
                            + " but is "
                            + status
            );
        }
    }

    private void touch() {
        this.updatedAt =
                Instant.now();
    }

    private static String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        return currency
                .trim()
                .toUpperCase();
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public MockProviderPaymentStatus getStatus() {
        return status;
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