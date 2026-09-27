package com.microservice.payments.payment.entity;

public enum PaymentStatus {

    PENDING,

    AUTHORIZED,

    CAPTURED,

    FAILED,

    VOIDED,

    REFUNDED
}