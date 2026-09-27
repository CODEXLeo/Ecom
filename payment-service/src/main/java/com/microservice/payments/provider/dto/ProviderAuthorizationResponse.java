package com.microservice.payments.provider.dto;

public record ProviderAuthorizationResponse(

        boolean successful,

        String providerPaymentId,

        String failureCode,

        String failureMessage
) {
}