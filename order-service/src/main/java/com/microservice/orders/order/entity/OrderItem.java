package com.microservice.orders.order.entity;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "order_item_id",
            nullable = false,
            updatable = false
    )
    private UUID orderItemId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    /**
     * ID of the product in Product Service.
     *
     * This is an external service identifier.
     * There is intentionally no database FK to Product Service.
     */
    @Column(
            name = "product_id",
            nullable = false
    )
    private UUID productId;

    /**
     * Historical product name at the time
     * the order was created.
     */
    @Column(
            name = "product_name_snapshot",
            nullable = false,
            length = 150
    )
    private String productNameSnapshot;

    /**
     * Historical unit price at the time
     * the order was created.
     */
    @Column(
            name = "unit_price_snapshot",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal unitPriceSnapshot;

    /**
     * Historical currency at the time
     * the order was created.
     */
    @Column(
            name = "currency_snapshot",
            nullable = false,
            length = 3
    )
    private String currencySnapshot;

    @Column(
            name = "quantity",
            nullable = false
    )
    private Integer quantity;

    @Column(
            name = "line_total",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal lineTotal;

    /**
     * Reservation created by Product Service.
     *
     * This is an external service identifier.
     *
     * It is stored so future workflows can:
     *
     * - commit the reservation after successful payment
     * - release the reservation after payment failure
     * - release the reservation during cancellation
     */
    @Column(
            name = "inventory_reservation_id",
            unique = true
    )
    private UUID inventoryReservationId;

    protected OrderItem() {
        // JPA
    }

    private OrderItem(
            UUID productId,
            String productNameSnapshot,
            BigDecimal unitPriceSnapshot,
            String currencySnapshot,
            Integer quantity
    ) {

        this.productId =
                Objects.requireNonNull(productId);

        this.productNameSnapshot =
                Objects.requireNonNull(
                        productNameSnapshot
                );

        this.unitPriceSnapshot =
                Objects.requireNonNull(
                        unitPriceSnapshot
                );

        this.currencySnapshot =
                Objects.requireNonNull(
                        currencySnapshot
                );

        this.quantity =
                Objects.requireNonNull(quantity);

        this.lineTotal =
                unitPriceSnapshot.multiply(
                        BigDecimal.valueOf(quantity)
                );
    }

    public static OrderItem create(
            UUID productId,
            String productNameSnapshot,
            BigDecimal unitPriceSnapshot,
            String currencySnapshot,
            Integer quantity
    ) {

        return new OrderItem(
                productId,
                productNameSnapshot,
                unitPriceSnapshot,
                currencySnapshot,
                quantity
        );
    }

    public void attachToOrder(Order order) {

        this.order =
                Objects.requireNonNull(order);
    }

    /**
     * Associates this order item with the inventory reservation
     * created for it.
     */
    public void attachInventoryReservation(
            UUID inventoryReservationId
    ) {

        if (inventoryReservationId == null) {

            throw new IllegalArgumentException(
                    "Inventory reservation ID must not be null"
            );
        }

        if (this.inventoryReservationId != null
                && !this.inventoryReservationId.equals(
                        inventoryReservationId
                )) {

            throw new IllegalStateException(
                    "Order item already has a different inventory reservation"
            );
        }

        this.inventoryReservationId =
                inventoryReservationId;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public Order getOrder() {
        return order;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public String getCurrencySnapshot() {
        return currencySnapshot;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public UUID getInventoryReservationId() {
        return inventoryReservationId;
    }
}