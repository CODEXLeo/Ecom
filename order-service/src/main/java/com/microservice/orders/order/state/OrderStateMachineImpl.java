package com.microservice.orders.order.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.microservice.orders.exception.InvalidOrderStateException;
import com.microservice.orders.order.entity.OrderStatus;

import org.springframework.stereotype.Component;

@Component
public class OrderStateMachineImpl
        implements OrderStateMachine {

    private final Map<OrderStatus, Set<OrderStatus>>
            transitions;

    public OrderStateMachineImpl() {

        Map<OrderStatus, Set<OrderStatus>> map =
                new EnumMap<>(OrderStatus.class);

        map.put(
                OrderStatus.PENDING,
                EnumSet.of(
                        OrderStatus.PLACED,
                        OrderStatus.CANCELLED
                )
        );

        map.put(
                OrderStatus.PLACED,
                EnumSet.of(
                        OrderStatus.CONFIRMED,
                        OrderStatus.CANCELLED
                )
        );

        map.put(
                OrderStatus.CONFIRMED,
                EnumSet.of(
                        OrderStatus.PROCESSING,
                        OrderStatus.CANCELLED
                )
        );

        map.put(
                OrderStatus.PROCESSING,
                EnumSet.of(
                        OrderStatus.SHIPPED,
                        OrderStatus.CANCELLED
                )
        );

        map.put(
                OrderStatus.SHIPPED,
                EnumSet.of(
                        OrderStatus.DELIVERED
                )
        );

        map.put(
                OrderStatus.DELIVERED,
                EnumSet.noneOf(OrderStatus.class)
        );

        map.put(
                OrderStatus.CANCELLED,
                EnumSet.noneOf(OrderStatus.class)
        );

        this.transitions = Map.copyOf(map);
    }

    @Override
    public boolean canTransition(
            OrderStatus current,
            OrderStatus target
    ) {

        Set<OrderStatus> allowed =
                transitions.get(current);

        return allowed != null
                && allowed.contains(target);
    }

    @Override
    public void validateTransition(
            OrderStatus current,
            OrderStatus target
    ) {

        if (!canTransition(current, target)) {

            throw new InvalidOrderStateException(
                    "Invalid order state transition: "
                            + current
                            + " -> "
                            + target
            );
        }
    }
}