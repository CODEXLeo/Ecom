package com.microservice.orders.integration.payment;

import java.util.UUID;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.integration.payment.dto.PaymentRequest;
import com.microservice.orders.integration.payment.dto.PaymentResponse;

import feign.FeignException;

import org.springframework.stereotype.Service;

@Service
public class PaymentClientImpl
        implements PaymentClient {

    private final PaymentFeignClient paymentFeignClient;

    public PaymentClientImpl(
            PaymentFeignClient paymentFeignClient
    ) {
        this.paymentFeignClient =
                paymentFeignClient;
    }

    @Override
    public PaymentResponse authorize(
            PaymentRequest request,
            String idempotencyKey
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Payment authorization request must not be null"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency key must not be null or blank"
            );
        }

        try {

            PaymentResponse response =
                    paymentFeignClient.authorize(
                            request,
                            idempotencyKey
                    );

            if (response == null) {

                throw new DownstreamServiceException(
                        "Payment Service returned an empty "
                                + "authorization response"
                );
            }

            return response;

        } catch (
                DownstreamServiceException exception
        ) {

            throw exception;

        } catch (FeignException exception) {

            throw new DownstreamServiceException(
                    "Payment Service authorization failed. HTTP "
                            + exception.status(),
                    exception
            );

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Payment Service",
                    exception
            );
        }
    }

    @Override
    public PaymentResponse capture(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                "capture",
                idempotencyKey
        );
    }

    @Override
    public PaymentResponse voidPayment(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                "void",
                idempotencyKey
        );
    }

    @Override
    public PaymentResponse refund(
            UUID paymentId,
            String idempotencyKey
    ) {

        return executeOperation(
                paymentId,
                "refund",
                idempotencyKey
        );
    }

    private PaymentResponse executeOperation(
            UUID paymentId,
            String operation,
            String idempotencyKey
    ) {

        if (paymentId == null) {

            throw new IllegalArgumentException(
                    "Payment ID must not be null"
            );
        }

        if (operation == null
                || operation.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment operation must not be null or blank"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency key must not be null or blank"
            );
        }

        try {

            PaymentResponse response =
                    paymentFeignClient.operate(
                            paymentId,
                            operation,
                            idempotencyKey
                    );

            if (response == null) {

                throw new DownstreamServiceException(
                        "Payment Service returned an empty "
                                + operation
                                + " response"
                );
            }

            return response;

        } catch (
                DownstreamServiceException exception
        ) {

            throw exception;

        } catch (FeignException exception) {

            throw new DownstreamServiceException(
                    "Payment Service "
                            + operation
                            + " failed. HTTP "
                            + exception.status(),
                    exception
            );

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Payment Service",
                    exception
            );
        }
    }
}