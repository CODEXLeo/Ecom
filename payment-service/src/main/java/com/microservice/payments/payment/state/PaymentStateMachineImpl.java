package com.microservice.payments.payment.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.microservice.payments.exception.InvalidPaymentStateException;
import com.microservice.payments.payment.entity.PaymentStatus;

import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachineImpl implements PaymentStateMachine {

    private final Map<PaymentStatus, Set<PaymentStatus>> transitions;

    public PaymentStateMachineImpl() {

        Map<
                PaymentStatus,
                Set<PaymentStatus>
                > map =
                new EnumMap<>(PaymentStatus.class);

        map.put(
                PaymentStatus.PENDING,
                EnumSet.of(
                        PaymentStatus.AUTHORIZED,
                        PaymentStatus.FAILED
                )
        );

        map.put(
                PaymentStatus.AUTHORIZED,
                EnumSet.of(
                        PaymentStatus.CAPTURED,
                        PaymentStatus.VOIDED,
                        PaymentStatus.FAILED
                )
        );

        map.put(
                PaymentStatus.CAPTURED,
                EnumSet.of(
                        PaymentStatus.REFUNDED
                )
        );

        map.put(
                PaymentStatus.FAILED,
                EnumSet.noneOf(
                        PaymentStatus.class
                )
        );

        map.put(
                PaymentStatus.VOIDED,
                EnumSet.noneOf(
                        PaymentStatus.class
                )
        );

        map.put(
                PaymentStatus.REFUNDED,
                EnumSet.noneOf(
                        PaymentStatus.class
                )
        );

        this.transitions =
                Map.copyOf(map);
    }

    @Override
    public boolean canTransition(
            PaymentStatus current,
            PaymentStatus target
    ) {

        Set<PaymentStatus> allowed =
                transitions.get(current);

        return allowed != null
                && allowed.contains(target);
    }

    @Override
    public void validateTransition(
            PaymentStatus current,
            PaymentStatus target
    ) {

        if (!canTransition(
                current,
                target
        )) {

            throw new InvalidPaymentStateException(
                    "Invalid payment transition: "
                            + current
                            + " -> "
                            + target
            );
        }
    }
}