package com.microservice.orders.integration.product;

import java.util.UUID;

import com.microservice.orders.integration.product.config.InventoryFeignConfiguration;
import com.microservice.orders.inventory.dto.request.ReserveInventoryRequest;
import com.microservice.orders.inventory.dto.response.InventoryOperationResponse;
import com.microservice.orders.inventory.dto.response.ReservationResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "product-service",
        contextId = "inventoryFeignClient",
        configuration = InventoryFeignConfiguration.class
)
public interface InventoryFeignClient {

    @PostMapping("/api/v1/inventory/reservations")
    ReservationResponse reserve(
            @RequestBody
            ReserveInventoryRequest request,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    );

    @PostMapping(
            "/api/v1/inventory/reservations/{reservationId}/commit"
    )
    InventoryOperationResponse commit(
            @PathVariable("reservationId")
            UUID reservationId
    );

    @PostMapping(
            "/api/v1/inventory/reservations/{reservationId}/release"
    )
    InventoryOperationResponse release(
            @PathVariable("reservationId")
            UUID reservationId
    );
}