package com.microservice.orders.order.entity;

/**
 * Lifecycle state of an order.
 *
 * PENDING is an internal orchestration state used while the order is
 * being created and inventory is being reserved.
 */
public enum OrderStatus {

    PENDING,

    PLACED,

    CONFIRMED,

    PROCESSING,

    SHIPPED,

    DELIVERED,

    CANCELLED
}