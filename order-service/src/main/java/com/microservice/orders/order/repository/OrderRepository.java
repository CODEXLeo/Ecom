package com.microservice.orders.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.microservice.orders.order.entity.Order;
import com.microservice.orders.order.entity.OrderStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import org.springframework.data.repository.query.Param;

public interface OrderRepository
        extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderIdAndUserId(
            UUID orderId,
            UUID userId
    );


    @Query("""
            select distinct o
            from Order o
            left join fetch o.items
            where o.orderId = :orderId
              and o.userId = :userId
            """)
    Optional<Order> findByOrderIdAndUserIdWithItems(
            @Param("orderId")
            UUID orderId,

            @Param("userId")
            UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.orderId = :orderId
              and o.userId = :userId
            """)
    Optional<Order> findByOrderIdAndUserIdForUpdate(
            @Param("orderId")
            UUID orderId,

            @Param("userId")
            UUID userId
    );

    Page<Order> findByUserIdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    List<Order> findByStatus(
            OrderStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.orderId = :orderId
            """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId")
            UUID orderId
    );
}