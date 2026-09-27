package com.microservice.payments.payment.controller;

import java.util.UUID;

import com.microservice.payments.payment.dto.request.AuthorizePaymentRequest;
import com.microservice.payments.payment.dto.response.PaymentResponse;
import com.microservice.payments.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(
        name = "Payments",
        description = "Payment management APIs"
)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(
            PaymentService paymentService
    ) {
        this.paymentService =
                paymentService;
    }

    // ================================================================
    // CUSTOMER PAYMENT LOOKUP
    // ================================================================

    @GetMapping("/{paymentId}")
    @Operation(
            summary = "Get payment",
            description =
                    "Returns a payment belonging to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment returned"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found"
            )
    })
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId
    ) {
        return ResponseEntity.ok(
                paymentService.getPayment(
                        paymentId
                )
        );
    }

    // ================================================================
    // INTERNAL AUTHORIZE
    // ================================================================

    @PostMapping("/internal/authorize")
    @Operation(
            summary = "Authorize payment internally"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description =
                            "Payment authorization completed"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description =
                            "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description =
                            "Order Service identity required"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description =
                            "Payment/idempotency conflict"
            )
    })
    public ResponseEntity<PaymentResponse> authorize(
            @Valid
            @RequestBody
            AuthorizePaymentRequest request,

            @Parameter(
                    description =
                            "Stable authorization idempotency key",
                    required = true
            )
            @RequestHeader(
                    name = "Idempotency-Key",
                    required = true
            )
            String idempotencyKey
    ) {
        return ResponseEntity
                .status(201)
                .body(
                        paymentService.authorize(
                                request,
                                idempotencyKey
                        )
                );
    }

    // ================================================================
    // INTERNAL CAPTURE
    // ================================================================

    @PostMapping(
            "/internal/{paymentId}/capture"
    )
    @Operation(
            summary = "Capture an authorized payment"
    )
    public ResponseEntity<PaymentResponse> capture(
            @PathVariable UUID paymentId,

            @RequestHeader(
                    name = "Idempotency-Key",
                    required = true
            )
            String idempotencyKey
    ) {
        return ResponseEntity.ok(
                paymentService.capture(
                        paymentId,
                        idempotencyKey
                )
        );
    }

    // ================================================================
    // INTERNAL VOID
    // ================================================================

    @PostMapping(
            "/internal/{paymentId}/void"
    )
    @Operation(
            summary = "Void an authorized payment"
    )
    public ResponseEntity<PaymentResponse> voidPayment(
            @PathVariable UUID paymentId,

            @RequestHeader(
                    name = "Idempotency-Key",
                    required = true
            )
            String idempotencyKey
    ) {
        return ResponseEntity.ok(
                paymentService.voidPayment(
                        paymentId,
                        idempotencyKey
                )
        );
    }

    // ================================================================
    // INTERNAL REFUND
    // ================================================================

    @PostMapping(
            "/internal/{paymentId}/refund"
    )
    @Operation(
            summary = "Refund a captured payment"
    )
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID paymentId,

            @RequestHeader(
                    name = "Idempotency-Key",
                    required = true
            )
            String idempotencyKey
    ) {
        return ResponseEntity.ok(
                paymentService.refund(
                        paymentId,
                        idempotencyKey
                )
        );
    }
}