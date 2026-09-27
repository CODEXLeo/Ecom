package com.microservice.orders.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "service.mtls")
public record MtlsProperties(

        String clientKeyStorePath,

        String clientKeyStorePassword,

        String trustStorePath,

        String trustStorePassword

) {
}