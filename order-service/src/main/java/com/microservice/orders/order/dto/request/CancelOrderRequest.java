package com.microservice.orders.order.dto.request;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Size(
                max = 500,
                message = "Cancellation reason must not exceed 500 characters"
        )
        String reason

) {
}