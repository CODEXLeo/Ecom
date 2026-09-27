package com.microservice.payments.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_order_id",
                        columnNames = "order_id"
                )
        }
)
public class Payment {

    @Id
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
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

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
    private PaymentStatus status;

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

    @Column(
            name = "provider_payment_id",
            length = 150
    )
    private String providerPaymentId;

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

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected Payment() {
        // JPA
    }

    private Payment(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            String currency
    ) {
        this.paymentId =
                Objects.requireNonNull(paymentId);

        this.orderId =
                Objects.requireNonNull(orderId);

        this.userId =
                Objects.requireNonNull(userId);

        this.paymentMethod =
                Objects.requireNonNull(paymentMethod);

        this.amount =
                Objects.requireNonNull(amount);

        this.currency =
                normalizeCurrency(currency);

        this.status =
                PaymentStatus.PENDING;

        Instant now =
                Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment create(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            String currency
    ) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (paymentMethod == PaymentMethod.COD) {
            throw new IllegalArgumentException(
                    "COD must not be processed by Payment Service"
            );
        }

        return new Payment(
                paymentId,
                orderId,
                userId,
                paymentMethod,
                amount,
                currency
        );
    }

    public void authorize(
            String providerPaymentId
    ) {
        requireStatus(PaymentStatus.PENDING);

        this.status =
                PaymentStatus.AUTHORIZED;

        this.providerPaymentId =
                Objects.requireNonNull(
                        providerPaymentId
                );

        this.authorizedAt =
                Instant.now();

        touch();
    }

    public void capture() {
        requireStatus(
                PaymentStatus.AUTHORIZED
        );

        this.status =
                PaymentStatus.CAPTURED;

        this.capturedAt =
                Instant.now();

        touch();
    }

    public void fail(
            String failureCode,
            String failureMessage
    ) {
        if (status != PaymentStatus.PENDING
                && status != PaymentStatus.AUTHORIZED) {

            throw new IllegalStateException(
                    "Payment cannot be failed from state "
                            + status
            );
        }

        this.status =
                PaymentStatus.FAILED;

        this.failureCode =
                failureCode;

        this.failureMessage =
                failureMessage;

        touch();
    }

    public void voidPayment() {
        requireStatus(
                PaymentStatus.AUTHORIZED
        );

        this.status =
                PaymentStatus.VOIDED;

        this.voidedAt =
                Instant.now();

        touch();
    }

    public void refund() {
        requireStatus(
                PaymentStatus.CAPTURED
        );

        this.status =
                PaymentStatus.REFUNDED;

        this.refundedAt =
                Instant.now();

        touch();
    }

    private void requireStatus(
            PaymentStatus expected
    ) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Payment must be "
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

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
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

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Long getVersion() {
        return version;
    }
}