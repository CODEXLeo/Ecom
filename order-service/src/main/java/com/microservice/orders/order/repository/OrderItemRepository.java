package com.microservice.orders.order.repository;

import java.util.List;
import java.util.UUID;

import com.microservice.orders.order.entity.OrderItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderOrderId(UUID orderId);
}