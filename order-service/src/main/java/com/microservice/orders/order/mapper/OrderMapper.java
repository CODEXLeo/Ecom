package com.microservice.orders.order.mapper;

import java.util.List;

import com.microservice.orders.order.dto.response.OrderItemResponse;
import com.microservice.orders.order.dto.response.OrderResponse;
import com.microservice.orders.order.dto.response.OrderSummaryResponse;
import com.microservice.orders.order.entity.Order;
import com.microservice.orders.order.entity.OrderItem;

import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(
            Order order
    ) {

        List<OrderItemResponse> items =
                order.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList();

        return new OrderResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getPaymentId(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getSubtotal(),
                order.getTotalAmount(),
                order.getCurrency(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCancelledAt(),
                order.getCancellationReason()
        );
    }

    public OrderSummaryResponse toSummaryResponse(
            Order order
    ) {
        return new OrderSummaryResponse(
                order.getOrderId(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt()
        );
    }

    private OrderItemResponse toItemResponse(
            OrderItem item
    ) {
        return new OrderItemResponse(
                item.getOrderItemId(),
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getUnitPriceSnapshot(),
                item.getCurrencySnapshot(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}