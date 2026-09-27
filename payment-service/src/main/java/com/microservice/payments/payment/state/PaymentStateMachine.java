package com.microservice.payments.payment.state;

import com.microservice.payments.payment.entity.PaymentStatus;

public interface PaymentStateMachine {

    boolean canTransition(
            PaymentStatus current,
            PaymentStatus target
    );

    void validateTransition(
            PaymentStatus current,
            PaymentStatus target
    );
}