package com.microservice.orders.order.dto.request;

import java.util.List;

import com.microservice.orders.order.entity.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(

        @NotEmpty(
                message = "Order must contain at least one item"
        )
        List<@Valid OrderItemRequest> items,

        @NotNull(
                message = "Payment method is required"
        )
        PaymentMethod paymentMethod

) {
}