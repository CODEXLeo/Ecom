package com.microservice.orders.order.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

    @Id
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

    /**
     * Payment Service payment ID.
     *
     * This is intentionally not a database foreign key because
     * Payment Service owns its own database.
     * IMPORTANT:
     *
     * The payment is created after the Order row is initially
     * persisted. Therefore this field MUST remain updatable.
     */
    @Column(
            name = "payment_id"
    )
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 30
    )
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_status",
            nullable = false,
            length = 30
    )
    private PaymentStatus paymentStatus;

    @Column(
            name = "subtotal",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal subtotal;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
            name = "currency",
            nullable = false,
            length = 3
    )
    private String currency;

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

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(
            name = "cancellation_reason",
            length = 500
    )
    private String cancellationReason;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    @OneToMany(
            mappedBy = "order",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<OrderItem> items =
            new ArrayList<>();

    protected Order() {
        // JPA
    }

    private Order(
            UUID orderId,
            UUID userId,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            String currency
    ) {
        this.orderId =
                Objects.requireNonNull(orderId);

        this.userId =
                Objects.requireNonNull(userId);

        this.paymentMethod =
                Objects.requireNonNull(paymentMethod);

        this.paymentStatus =
                Objects.requireNonNull(paymentStatus);

        this.currency =
                normalizeCurrency(currency);

        this.status =
                OrderStatus.PENDING;

        this.subtotal =
                BigDecimal.ZERO;

        this.totalAmount =
                BigDecimal.ZERO;

        Instant now =
                Instant.now();

        this.createdAt =
                now;

        this.updatedAt =
                now;
    }

    public static Order create(
            UUID userId,
            PaymentMethod paymentMethod,
            String currency
    ) {
        return create(
                UUID.randomUUID(),
                userId,
                paymentMethod,
                currency
        );
    }

    public static Order create(
            UUID orderId,
            UUID userId,
            PaymentMethod paymentMethod,
            String currency
    ) {
        PaymentStatus paymentStatus =
                paymentMethod == PaymentMethod.COD
                        ? PaymentStatus.NOT_REQUIRED
                        : PaymentStatus.PENDING;

        return new Order(
                orderId,
                userId,
                paymentMethod,
                paymentStatus,
                currency
        );
    }

    public void addItem(
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            String currency,
            int quantity
    ) {
        if (productId == null) {
            throw new IllegalArgumentException(
                    "Product ID is required"
            );
        }

        if (productName == null
                || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name is required"
            );
        }

        if (unitPrice == null
                || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Unit price must be greater than zero"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive"
            );
        }

        String normalizedCurrency =
                normalizeCurrency(currency);

        if (!this.currency.equals(
                normalizedCurrency
        )) {
            throw new IllegalArgumentException(
                    "Order item currency does not match order currency"
            );
        }

        OrderItem item =
                OrderItem.create(
                        productId,
                        productName.trim(),
                        unitPrice,
                        normalizedCurrency,
                        quantity
                );

        // OrderItem owns the JPA relationship through its `order` field.
        // The parent collection alone is not enough because `mappedBy = "order"`
        // makes the child side the owning side of the relationship.
        item.attachToOrder(this);
        items.add(item);

        recalculateTotals();
    }

    public void attachPayment(
            UUID paymentId
    ) {
        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "Payment ID must not be null"
            );
        }

        if (this.paymentId != null
                && !this.paymentId.equals(paymentId)) {
            throw new IllegalStateException(
                    "Order already has a different payment ID"
            );
        }

        this.paymentId =
                paymentId;

        touch();
    }

    public void markPlaced() {
        requireStatus(
                OrderStatus.PENDING
        );

        this.status =
                OrderStatus.PLACED;

        touch();
    }

    public void confirm() {
        if (this.status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Order must be PLACED before confirmation"
            );
        }

        this.status =
                OrderStatus.CONFIRMED;

        touch();
    }

    public void markPaymentAuthorized() {
        if (paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment must be PENDING before authorization"
            );
        }

        paymentStatus =
                PaymentStatus.AUTHORIZED;

        touch();
    }

    public void markPaymentCaptured() {
        if (paymentStatus != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment must be AUTHORIZED before capture"
            );
        }

        paymentStatus =
                PaymentStatus.CAPTURED;

        touch();
    }

    public void markPaymentFailed() {
        if (paymentStatus != PaymentStatus.PENDING
                && paymentStatus != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment cannot be marked FAILED from "
                            + paymentStatus
            );
        }

        paymentStatus =
                PaymentStatus.FAILED;

        touch();
    }

    public void markPaymentVoided() {
        if (paymentStatus != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment must be AUTHORIZED before void"
            );
        }

        paymentStatus =
                PaymentStatus.VOIDED;

        touch();
    }

    public void markPaymentRefunded() {
        if (paymentStatus != PaymentStatus.CAPTURED) {
            throw new IllegalStateException(
                    "Payment must be CAPTURED before refund"
            );
        }

        paymentStatus =
                PaymentStatus.REFUNDED;

        touch();
    }

    public void cancel(
            String reason
    ) {
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Order is already cancelled"
            );
        }

        if (status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Delivered order cannot be cancelled"
            );
        }

        this.status =
                OrderStatus.CANCELLED;

        this.cancelledAt =
                Instant.now();

        this.cancellationReason =
                normalizeCancellationReason(
                        reason
                );

        touch();
    }

    private void recalculateTotals() {
        BigDecimal calculatedSubtotal =
                items.stream()
                        .map(OrderItem::getLineTotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        this.subtotal =
                calculatedSubtotal;

        /*
         * Shipping, tax, discounts and other charges
         * are not modelled yet.
         */
        this.totalAmount =
                calculatedSubtotal;

        touch();
    }

    private void requireStatus(
            OrderStatus expected
    ) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Order must be "
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

    private static String normalizeCancellationReason(
            String reason
    ) {
        if (reason == null) {
            return null;
        }

        String normalized =
                reason.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(
                items
        );
    }
}