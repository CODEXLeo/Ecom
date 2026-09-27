package com.microservice.payments.provider;

import com.microservice.payments.provider.dto.ProviderAuthorizationRequest;
import com.microservice.payments.provider.dto.ProviderAuthorizationResponse;
import com.microservice.payments.provider.dto.ProviderOperationResponse;

public interface PaymentProvider {

    ProviderAuthorizationResponse authorize(
            ProviderAuthorizationRequest request,
            String idempotencyKey
    );

    ProviderOperationResponse capture(
            String providerPaymentId,
            String idempotencyKey
    );

    ProviderOperationResponse voidPayment(
            String providerPaymentId,
            String idempotencyKey
    );

    ProviderOperationResponse refund(
            String providerPaymentId,
            String idempotencyKey
    );
}