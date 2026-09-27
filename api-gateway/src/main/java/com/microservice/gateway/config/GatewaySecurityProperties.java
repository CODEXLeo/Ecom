package com.microservice.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(

        String accessTokenCookieName,

        String issuer,

        String publicKeyPath

) {
}