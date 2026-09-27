package com.microservice.orders.order.service;

import java.util.List;
import java.util.UUID;

import com.microservice.orders.order.dto.response.OrderResponse;

public record OrderCancellationResult(

        OrderResponse response,

        List<UUID> reservationIds

) {
}