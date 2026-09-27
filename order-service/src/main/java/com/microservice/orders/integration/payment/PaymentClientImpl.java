package com.microservice.orders.integration.payment;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.integration.payment.dto.PaymentRequest;
import com.microservice.orders.integration.payment.dto.PaymentResponse;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PaymentClientImpl
        implements PaymentClient {

    private final WebClient paymentServiceWebClient;

    public PaymentClientImpl(
            @Qualifier("paymentServiceWebClient")
            WebClient paymentServiceWebClient
    ) {
        this.paymentServiceWebClient =
                paymentServiceWebClient;
    }

    @Override
    public PaymentResponse authorize(
            PaymentRequest request,
            String idempotencyKey
    ) {
        try {
            PaymentResponse response =
                    paymentServiceWebClient
                            .post()
                            .uri(
                                    "/api/v1/payments/internal/authorize"
                            )
                            .header(
                                    "Idempotency-Key",
                                    idempotencyKey
                            )
                            .bodyValue(request)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::isError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty(
                                                            ""
                                                    )
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Payment Service authorization failed. HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .bodyToMono(
                                    PaymentResponse.class
                            )
                            .block();

            if (response == null) {
                throw new DownstreamServiceException(
                        "Payment Service returned an empty authorization response"
                );
            }

            return response;

        } catch (
                DownstreamServiceException exception
        ) {
            throw exception;

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

        try {
            PaymentResponse response =
                    paymentServiceWebClient
                            .post()
                            .uri(
                                    "/api/v1/payments/internal/{paymentId}/{operation}",
                                    paymentId,
                                    operation
                            )
                            .header(
                                    "Idempotency-Key",
                                    idempotencyKey
                            )
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::isError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty(
                                                            ""
                                                    )
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Payment Service "
                                                                            + operation
                                                                            + " failed. HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .bodyToMono(
                                    PaymentResponse.class
                            )
                            .block();

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

        } catch (Exception exception) {
            throw new DownstreamServiceException(
                    "Unable to communicate with Payment Service",
                    exception
            );
        }
    }
}