package com.microservice.orders.order.controller;

import java.net.URI;
import java.util.UUID;

import com.microservice.orders.order.dto.request.CancelOrderRequest;
import com.microservice.orders.order.dto.request.CreateOrderRequest;
import com.microservice.orders.order.dto.response.OrderPageResponse;
import com.microservice.orders.order.dto.response.OrderResponse;
import com.microservice.orders.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders",description = "Customer order management APIs"
)
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ================================================================
    // CREATE ORDER
    // ================================================================

    @PostMapping
    @Operation(
            summary = "Create an order",
            description = "Creates an order for the authenticated user. " + "The Idempotency-Key header is required so " + "retries cannot create duplicate orders."
    )
    @ApiResponses({

            @ApiResponse(responseCode = "201", description = "Order created successfully",
            		content = @Content(schema = @Schema(implementation = OrderResponse.class))),

            @ApiResponse(responseCode = "400", description = "Invalid order request"),

            @ApiResponse(responseCode = "401", description = "Authentication required"),

            @ApiResponse(responseCode = "409", description = "Inventory or idempotency conflict"),

            @ApiResponse(responseCode = "503", description = "Downstream service unavailable")
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Unique key used to make order creation idempotent", required = true, example = "checkout-2026-09-13-8f3c7e")
            @RequestHeader(name = "Idempotency-Key", required = true)
            String idempotencyKey) {

        OrderResponse response = orderService.createOrder(request, idempotencyKey);
        URI location = URI.create("/api/v1/orders/" + response.orderId());
        return ResponseEntity.created(location).body(response);
    }

    // ================================================================
    // GET SINGLE ORDER
    // ================================================================

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Get an order",
            description =
                    "Returns one order belonging to the authenticated user."
    )
    @ApiResponses({

            @ApiResponse(
                    responseCode = "200",
                    description = "Order returned successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            OrderResponse.class
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),

            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    public ResponseEntity<OrderResponse> getOrder(

            @Parameter(
                    description = "Order UUID",
                    required = true
            )
            @PathVariable
            UUID orderId
    ) {

        return ResponseEntity.ok(
                orderService.getOrder(
                        orderId
                )
        );
    }

    // ================================================================
    // GET ORDER HISTORY
    // ================================================================

    @GetMapping
    @Operation(
            summary = "Get order history",
            description =
                    "Returns the authenticated user's orders, newest first."
    )
    @ApiResponses({

            @ApiResponse(
                    responseCode = "200",
                    description =
                            "Order history returned successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            OrderPageResponse.class
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters"
            ),

            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            )
    })
    public ResponseEntity<OrderPageResponse> getOrders(

            @Parameter(
                    description =
                            "Zero-based page number",
                    example = "0"
            )
            @RequestParam(
                    name = "page",
                    defaultValue = "0"
            )
            int page,

            @Parameter(
                    description =
                            "Number of orders per page. Maximum 100.",
                    example = "20"
            )
            @RequestParam(
                    name = "size",
                    defaultValue = "20"
            )
            int size
    ) {

        return ResponseEntity.ok(
                orderService.getOrders(
                        page,
                        size
                )
        );
    }

    // ================================================================
    // CANCEL ORDER
    // ================================================================

    @PostMapping("/{orderId}/cancel")
    @Operation(
            summary = "Cancel an order",
            description =
                    "Cancels an eligible order belonging to the "
                            + "authenticated user and releases its "
                            + "inventory reservations."
    )
    @ApiResponses({

            @ApiResponse(
                    responseCode = "200",
                    description =
                            "Order cancelled successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            OrderResponse.class
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid cancellation request"
            ),

            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),

            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            ),

            @ApiResponse(
                    responseCode = "409",
                    description =
                            "Order cannot be cancelled from its current state"
            ),

            @ApiResponse(
                    responseCode = "503",
                    description =
                            "Inventory release failed"
            )
    })
    public ResponseEntity<OrderResponse> cancelOrder(

            @Parameter(description = "Order UUID", required = true)
            @PathVariable
            UUID orderId,

            @Valid
            @RequestBody(required = false)
            CancelOrderRequest request) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId,request));
    }
}