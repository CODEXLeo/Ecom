package com.microservice.orders.order.state;

import com.microservice.orders.order.entity.OrderStatus;

public interface OrderStateMachine {

    boolean canTransition(
            OrderStatus current,
            OrderStatus target
    );

    void validateTransition(
            OrderStatus current,
            OrderStatus target
    );
}