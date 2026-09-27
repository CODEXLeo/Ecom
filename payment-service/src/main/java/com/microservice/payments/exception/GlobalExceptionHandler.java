package com.microservice.payments.exception;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    // ================================================================
    // PAYMENT NOT FOUND
    // ================================================================

    @ExceptionHandler(
            PaymentNotFoundException.class
    )
    public ProblemDetail handlePaymentNotFound(
            PaymentNotFoundException exception
    ) {

        return problem(
                HttpStatus.NOT_FOUND,
                "Payment not found",
                exception.getMessage()
        );
    }

    // ================================================================
    // PAYMENT CONFLICT
    // ================================================================

    @ExceptionHandler(
            PaymentConflictException.class
    )
    public ProblemDetail handlePaymentConflict(
            PaymentConflictException exception
    ) {

        return problem(
                HttpStatus.CONFLICT,
                "Payment conflict",
                exception.getMessage()
        );
    }

    // ================================================================
    // INVALID PAYMENT STATE
    // ================================================================

    @ExceptionHandler(
            InvalidPaymentStateException.class
    )
    public ProblemDetail handlePaymentState(
            InvalidPaymentStateException exception
    ) {

        return problem(
                HttpStatus.CONFLICT,
                "Invalid payment state",
                exception.getMessage()
        );
    }

    // ================================================================
    // IDEMPOTENCY CONFLICT
    // ================================================================

    @ExceptionHandler(
            IdempotencyKeyConflictException.class
    )
    public ProblemDetail handleIdempotencyConflict(
            IdempotencyKeyConflictException exception
    ) {

        return problem(
                HttpStatus.CONFLICT,
                "Idempotency conflict",
                exception.getMessage()
        );
    }

    // ================================================================
    // IDEMPOTENCY IN PROGRESS
    // ================================================================

    @ExceptionHandler(
            IdempotencyInProgressException.class
    )
    public ProblemDetail handleIdempotencyInProgress(
            IdempotencyInProgressException exception
    ) {

        return problem(
                HttpStatus.CONFLICT,
                "Request already in progress",
                exception.getMessage()
        );
    }

    // ================================================================
    // IDEMPOTENCY FAILURE REPLAY
    // ================================================================

    @ExceptionHandler(
            IdempotencyReplayException.class
    )
    public ResponseEntity<String> handleIdempotencyReplay(
            IdempotencyReplayException exception
    ) {

        return ResponseEntity
                .status(
                        exception.getResponseStatus()
                )
                .contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                )
                .body(
                        exception.getResponseBody()
                );
    }

    // ================================================================
    // PAYMENT PROVIDER FAILURE
    // ================================================================

    @ExceptionHandler(
            PaymentProviderException.class
    )
    public ProblemDetail handleProviderFailure(
            PaymentProviderException exception
    ) {

        log.error(
                "Payment provider failure",
                exception
        );

        return problem(
                HttpStatus.BAD_GATEWAY,
                "Payment provider unavailable",
                "The payment provider could not complete "
                        + "the requested operation."
        );
    }

    // ================================================================
    // DOWNSTREAM FAILURE
    // ================================================================

    @ExceptionHandler(
            DownstreamServiceException.class
    )
    public ProblemDetail handleDownstreamFailure(
            DownstreamServiceException exception
    ) {

        log.error(
                "Downstream service failure",
                exception
        );

        return problem(
                HttpStatus.BAD_GATEWAY,
                "Downstream service failure",
                "A required downstream service could not "
                        + "complete the request."
        );
    }

    // ================================================================
    // UNEXPECTED ERROR
    // ================================================================

    @ExceptionHandler(
            Exception.class
    )
    public ProblemDetail handleUnexpected(
            Exception exception
    ) {

        /*
         * Never expose the stack trace to the caller.
         *
         * But DO log it so that a genuine programming/database/
         * serialization failure is diagnosable from the Payment
         * Service terminal.
         */
        log.error(
                "Unexpected exception while processing payment",
                exception
        );

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal payment service error",
                "An unexpected error occurred while "
                        + "processing the payment."
        );
    }

    // ================================================================
    // PROBLEM DETAIL
    // ================================================================

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatus(
                        status
                );

        problem.setTitle(
                title
        );

        problem.setDetail(
                detail
        );

        problem.setType(
                URI.create(
                        "https://microservice.local/problems/"
                                + status.value()
                )
        );

        return problem;
    }
}