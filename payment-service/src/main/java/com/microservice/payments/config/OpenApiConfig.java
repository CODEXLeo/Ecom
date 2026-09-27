package com.microservice.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "Payment Service API"
                                )
                                .description(
                                        "Payment management API "
                                                + "for the microservice "
                                                + "commerce platform."
                                )
                                .version("v1")
                                .contact(
                                        new Contact()
                                                .name(
                                                        "Microservice Platform"
                                                )
                                )
                );
    }
}