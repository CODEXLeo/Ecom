package com.microservice.payments.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        String issuer,

        String publicKeyPath
) {
}