package com.microservice.products.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        /**
         * Expected JWT issuer.
         */
        String issuer,

        /**
         * Filesystem path to the RSA public key used
         * to verify JWT signatures.
         */
        String publicKeyPath

) {
}