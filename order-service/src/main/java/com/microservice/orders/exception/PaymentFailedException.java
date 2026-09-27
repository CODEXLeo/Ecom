package com.microservice.orders.exception;

public class PaymentFailedException extends RuntimeException {

    private final String failureCode;

    public PaymentFailedException(
            String message,
            String failureCode
    ) {
        super(message);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}