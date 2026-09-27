package com.microservice.orders.order.entity;

public enum PaymentStatus {

    /**
     * Used for COD orders where no online payment
     * is required during checkout.
     */
    NOT_REQUIRED,

    /**
     * Online payment has not yet been authorized.
     */
    PENDING,

    /**
     * Provider has authorized the payment.
     */
    AUTHORIZED,

    /**
     * Provider has captured the payment.
     */
    CAPTURED,

    /**
     * Provider explicitly rejected the payment.
     */
    FAILED,

    /**
     * An authorized payment was successfully voided.
     */
    VOIDED,

    /**
     * A captured payment was successfully refunded.
     */
    REFUNDED
}