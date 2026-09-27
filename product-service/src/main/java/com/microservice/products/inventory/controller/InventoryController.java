package com.microservice.products.inventory.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.products.inventory.dto.request.ReservationRequest;
import com.microservice.products.inventory.dto.response.ReservationResponse;
import com.microservice.products.inventory.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(
        name = "Inventory",
        description =
                "Internal inventory reservation APIs"
)
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(
            InventoryService inventoryService) {

        this.inventoryService = inventoryService;
    }

    /*
     * ============================================================
     * RESERVE
     * ============================================================
     *
     * INTERNAL SERVICE API
     *
     * Only Order Service may call this endpoint.
     *
     * Authentication:
     *
     *     mTLS client certificate
     *
     * Authorization:
     *
     *     ROLE_SERVICE_ORDER
     */

    @PostMapping("/reservations")
    @PreAuthorize("hasRole('SERVICE_ORDER')")
    @Operation(
            summary = "Reserve inventory",
            description =
                    "Internal service-to-service API. "
                    + "Requires the Order Service mTLS identity. "
                    + "The Idempotency-Key makes retries safe.",
            security = {
                    @SecurityRequirement(name = "mtls")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description =
                            "Inventory reserved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description =
                            "Invalid reservation request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description =
                            "Service authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description =
                            "Caller is not Order Service"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description =
                            "Product not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description =
                            "Insufficient stock or idempotency conflict"
            )
    })
    public ResponseEntity<ReservationResponse> reserve(

            @Valid
            @RequestBody
            ReservationRequest request,

            @Parameter(
                    name = "Idempotency-Key",
                    description =
                            "Unique key for this reservation attempt.",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(
                            type = "string",
                            maxLength = 200
                    )
            )
            @RequestHeader("Idempotency-Key")
            String idempotencyKey) {

        return ResponseEntity
                .status(201)
                .body(
                        inventoryService.reserve(
                                request,
                                idempotencyKey
                        )
                );
    }

    /*
     * ============================================================
     * COMMIT
     * ============================================================
     */

    @PostMapping(
            "/reservations/{reservationId}/commit"
    )
    @PreAuthorize("hasRole('SERVICE_ORDER')")
    @Operation(
            summary = "Commit inventory reservation",
            description =
                    "Internal Order Service API. "
                    + "Permanently commits the reservation.",
            security = {
                    @SecurityRequirement(name = "mtls")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reservation committed"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description =
                            "Service authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description =
                            "Caller is not Order Service"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description =
                            "Reservation not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description =
                            "Reservation cannot be committed"
            )
    })
    public ResponseEntity<ReservationResponse> commit(

            @Parameter(
                    description = "Reservation UUID",
                    required = true
            )
            @PathVariable
            UUID reservationId) {

        return ResponseEntity.ok(
                inventoryService.commit(
                        reservationId
                )
        );
    }

    /*
     * ============================================================
     * RELEASE
     * ============================================================
     */

    @PostMapping(
            "/reservations/{reservationId}/release"
    )
    @PreAuthorize("hasRole('SERVICE_ORDER')")
    @Operation(
            summary = "Release inventory reservation",
            description =
                    "Internal Order Service API. "
                    + "Releases reserved stock back into inventory.",
            security = {
                    @SecurityRequirement(name = "mtls")
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reservation released"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description =
                            "Service authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description =
                            "Caller is not Order Service"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description =
                            "Reservation not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description =
                            "Reservation cannot be released"
            )
    })
    public ResponseEntity<ReservationResponse> release(

            @Parameter(
                    description = "Reservation UUID",
                    required = true
            )
            @PathVariable
            UUID reservationId) {

        return ResponseEntity.ok(
                inventoryService.release(
                        reservationId
                )
        );
    }
}