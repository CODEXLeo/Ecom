package com.microservice.products.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory.reservation")
public record InventoryReservationProperties(

        /**
         * How long an inventory reservation remains valid.
         */
        Duration duration,

        /**
         * Delay between scheduled reservation-expiration checks,
         * expressed in milliseconds.
         */
        long expiryCheckDelayMs

) {
}