package com.microservice.payments.exception;

public class InvalidPaymentStateException
        extends RuntimeException {

    public InvalidPaymentStateException(
            String message
    ) {

        super(message);
    }
}