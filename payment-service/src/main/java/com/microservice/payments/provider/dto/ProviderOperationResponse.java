package com.microservice.payments.provider.dto;

public record ProviderOperationResponse(
        boolean successful,
        String failureCode,
        String failureMessage
) {

    public static ProviderOperationResponse success() {
        return new ProviderOperationResponse(
                true,
                null,
                null
        );
    }

    public static ProviderOperationResponse failure(
            String failureCode,
            String failureMessage
    ) {
        return new ProviderOperationResponse(
                false,
                failureCode,
                failureMessage
        );
    }
}