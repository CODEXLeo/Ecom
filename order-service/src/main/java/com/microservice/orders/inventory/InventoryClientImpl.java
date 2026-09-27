package com.microservice.orders.inventory;

import java.util.UUID;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.exception.InventoryReservationConflictException;
import com.microservice.orders.exception.InventoryReservationException;
import com.microservice.orders.inventory.dto.request.ReserveInventoryRequest;
import com.microservice.orders.inventory.dto.response.InventoryOperationResponse;
import com.microservice.orders.inventory.dto.response.ReservationResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class InventoryClientImpl implements InventoryClient {

    private final WebClient productInventoryServiceWebClient;

    public InventoryClientImpl(
            @Qualifier("productInventoryServiceWebClient")
            WebClient productInventoryServiceWebClient
    ) {
        this.productInventoryServiceWebClient =
                productInventoryServiceWebClient;
    }

    @Override
    public ReservationResponse reserve(
            UUID orderId,
            UUID productId,
            int quantity,
            String idempotencyKey
    ) {

        if (orderId == null) {
            throw new IllegalArgumentException(
                    "Order ID must not be null"
            );
        }

        if (productId == null) {
            throw new IllegalArgumentException(
                    "Product ID must not be null"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Inventory reservation quantity must be positive"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Inventory idempotency key must not be blank"
            );
        }

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        orderId,
                        productId,
                        quantity
                );

        try {

            ReservationResponse response =
                    productInventoryServiceWebClient
                            .post()
                            .uri(
                                    "/api/v1/inventory/reservations"
                            )
                            .header(
                                    "Idempotency-Key",
                                    idempotencyKey
                            )
                            .bodyValue(request)
                            .retrieve()
                            .onStatus(
                                    status ->
                                            status.value() == 409,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new InventoryReservationConflictException(
                                                                    "Inventory reservation conflict for product "
                                                                            + productId
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new InventoryReservationException(
                                                                    "Inventory Service rejected reservation. HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Inventory Service returned HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .bodyToMono(
                                    ReservationResponse.class
                            )
                            .block();

            if (response == null) {
                throw new DownstreamServiceException(
                        "Inventory Service returned an empty reservation response"
                );
            }

            return response;

        } catch (
                InventoryReservationConflictException
                        | InventoryReservationException
                        | DownstreamServiceException exception
        ) {

            throw exception;

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Inventory Service",
                    exception
            );
        }
    }

    @Override
    public InventoryOperationResponse commit(
            UUID reservationId
    ) {

        return executeOperation(
                reservationId,
                "commit"
        );
    }

    @Override
    public InventoryOperationResponse release(
            UUID reservationId
    ) {

        return executeOperation(
                reservationId,
                "release"
        );
    }

    private InventoryOperationResponse executeOperation(
            UUID reservationId,
            String operation
    ) {

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "Reservation ID must not be null"
            );
        }

        try {

            InventoryOperationResponse response =
                    productInventoryServiceWebClient
                            .post()
                            .uri(
                                    "/api/v1/inventory/reservations/{reservationId}/{operation}",
                                    reservationId,
                                    operation
                            )
                            .retrieve()
                            .onStatus(
                                    status ->
                                            status.value() == 404,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new InventoryReservationException(
                                                                    "Inventory reservation not found: "
                                                                            + reservationId
                                                            )
                                                    )
                            )
                            .onStatus(
                                    status ->
                                            status.value() == 409,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new InventoryReservationConflictException(
                                                                    "Inventory reservation cannot be "
                                                                            + operation
                                                                            + "d: "
                                                                            + reservationId
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new InventoryReservationException(
                                                                    "Inventory Service rejected "
                                                                            + operation
                                                                            + ". HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .map(body ->
                                                            new DownstreamServiceException(
                                                                    "Inventory Service returned HTTP "
                                                                            + clientResponse
                                                                                    .statusCode()
                                                                                    .value()
                                                            )
                                                    )
                            )
                            .bodyToMono(
                                    InventoryOperationResponse.class
                            )
                            .block();

            if (response == null) {
                throw new DownstreamServiceException(
                        "Inventory Service returned an empty "
                                + operation
                                + " response"
                );
            }

            return response;

        } catch (
                InventoryReservationConflictException
                        | InventoryReservationException
                        | DownstreamServiceException exception
        ) {

            throw exception;

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Inventory Service",
                    exception
            );
        }
    }
}