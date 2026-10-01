package com.microservice.orders.inventory;

import java.util.UUID;

import com.microservice.orders.exception.DownstreamServiceException;
import com.microservice.orders.exception.InventoryReservationConflictException;
import com.microservice.orders.exception.InventoryReservationException;
import com.microservice.orders.integration.product.InventoryFeignClient;
import com.microservice.orders.inventory.dto.request.ReserveInventoryRequest;
import com.microservice.orders.inventory.dto.response.InventoryOperationResponse;
import com.microservice.orders.inventory.dto.response.ReservationResponse;

import feign.FeignException;

import org.springframework.stereotype.Service;

@Service
public class InventoryClientImpl implements InventoryClient {

    private final InventoryFeignClient inventoryFeignClient;

    public InventoryClientImpl(
            InventoryFeignClient inventoryFeignClient
    ) {
        this.inventoryFeignClient =
                inventoryFeignClient;
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
                    inventoryFeignClient.reserve(
                            request,
                            idempotencyKey
                    );

            if (response == null) {
                throw new DownstreamServiceException(
                        "Inventory Service returned an empty reservation response"
                );
            }

            return response;

        } catch (FeignException.Conflict exception) {

            throw new InventoryReservationConflictException(
                    "Inventory reservation conflict for product "
                            + productId
            );

        } catch (FeignException exception) {

            throw new DownstreamServiceException(
                    "Inventory Service reservation failed. HTTP "
                            + exception.status(),
                    exception
            );

        } catch (DownstreamServiceException exception) {

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

            InventoryOperationResponse response;

            if ("commit".equals(operation)) {

                response =
                        inventoryFeignClient.commit(
                                reservationId
                        );

            } else if ("release".equals(operation)) {

                response =
                        inventoryFeignClient.release(
                                reservationId
                        );

            } else {

                throw new IllegalArgumentException(
                        "Unsupported inventory operation: "
                                + operation
                );
            }

            if (response == null) {
                throw new DownstreamServiceException(
                        "Inventory Service returned an empty "
                                + operation
                                + " response"
                );
            }

            return response;

        } catch (FeignException.NotFound exception) {

            throw new InventoryReservationException(
                    "Inventory reservation not found: "
                            + reservationId
            );

        } catch (FeignException.Conflict exception) {

            throw new InventoryReservationConflictException(
                    "Inventory reservation cannot be "
                            + operation
                            + "d: "
                            + reservationId
            );

        } catch (FeignException exception) {

            throw new DownstreamServiceException(
                    "Inventory Service "
                            + operation
                            + " failed. HTTP "
                            + exception.status(),
                    exception
            );

        } catch (DownstreamServiceException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new DownstreamServiceException(
                    "Unable to communicate with Inventory Service",
                    exception
            );
        }
    }
}