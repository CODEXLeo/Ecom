package com.microservice.orders.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(
            ProductNotFoundException exception
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Product Not Found",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/product-not-found"
        );
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ProblemDetail handleProductUnavailable(
            ProductUnavailableException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Product Unavailable",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/product-unavailable"
        );
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(
            OrderNotFoundException exception
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Order Not Found",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/order-not-found"
        );
    }

    @ExceptionHandler(EmptyOrderException.class)
    public ProblemDetail handleEmptyOrder(
            EmptyOrderException exception
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Empty Order",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/empty-order"
        );
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderState(
            InvalidOrderStateException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Invalid Order State",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/invalid-order-state"
        );
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            IdempotencyKeyConflictException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Idempotency Key Conflict",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/idempotency-conflict"
        );
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ProblemDetail handleIdempotencyInProgress(
            IdempotencyInProgressException exception
    ) {
        ProblemDetail detail =
                problem(
                        HttpStatus.CONFLICT,
                        "Request Already In Progress",
                        exception.getMessage(),
                        "https://microservice-platform.dev/problems/idempotency-in-progress"
                );

        detail.setProperty(
                "retryable",
                true
        );

        return detail;
    }

    @ExceptionHandler(InventoryReservationConflictException.class)
    public ProblemDetail handleInventoryConflict(
            InventoryReservationConflictException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Inventory Reservation Conflict",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/inventory-reservation-conflict"
        );
    }

    @ExceptionHandler(InventoryReservationException.class)
    public ProblemDetail handleInventoryReservation(
            InventoryReservationException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Inventory Reservation Failed",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/inventory-reservation"
        );
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail handlePaymentFailed(
            PaymentFailedException exception
    ) {
        ProblemDetail detail =
                problem(
                        HttpStatus.PAYMENT_REQUIRED,
                        "Payment Failed",
                        exception.getMessage(),
                        "https://microservice-platform.dev/problems/payment-failed"
                );

        detail.setProperty(
                "failureCode",
                exception.getFailureCode()
        );

        return detail;
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ProblemDetail handleDownstreamService(
            DownstreamServiceException exception
    ) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Downstream Service Unavailable",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/downstream-service-unavailable"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail detail =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Validation Failed",
                        "One or more request fields are invalid",
                        "https://microservice-platform.dev/problems/validation"
                );

        Map<String, String> errors =
                new LinkedHashMap<>();

        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        detail.setProperty(
                "errors",
                errors
        );

        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ProblemDetail detail =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Constraint Violation",
                        exception.getMessage(),
                        "https://microservice-platform.dev/problems/constraint-violation"
                );

        Map<String, String> errors =
                new LinkedHashMap<>();

        exception
                .getConstraintViolations()
                .forEach(violation ->
                        errors.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        detail.setProperty(
                "errors",
                errors
        );

        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/invalid-request"
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(
            IllegalStateException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Invalid Operation",
                exception.getMessage(),
                "https://microservice-platform.dev/problems/illegal-state"
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(
            Exception exception
    ) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                "https://microservice-platform.dev/problems/internal-error"
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String type
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail == null
                                ? title
                                : detail
                );

        problem.setTitle(title);
        problem.setType(
                URI.create(type)
        );

        return problem;
    }
}