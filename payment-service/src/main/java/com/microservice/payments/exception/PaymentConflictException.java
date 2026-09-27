package com.microservice.payments.exception;

public class PaymentConflictException
        extends RuntimeException {

    public PaymentConflictException(
            String message
    ) {
        super(message);
    }
}