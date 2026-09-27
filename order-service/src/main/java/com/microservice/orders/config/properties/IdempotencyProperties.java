package com.microservice.orders.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idempotency")
public record IdempotencyProperties(

        /**
         * How long an IN_PROGRESS idempotency record remains claimable
         * before another request can safely recover it.
         */
        Duration recordTtl

) {
}