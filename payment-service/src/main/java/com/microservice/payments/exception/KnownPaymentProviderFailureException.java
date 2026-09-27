package com.microservice.payments.exception;

public class KnownPaymentProviderFailureException
        extends PaymentProviderException {

    public KnownPaymentProviderFailureException(
            String message
    ) {
        super(message);
    }

    public KnownPaymentProviderFailureException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}