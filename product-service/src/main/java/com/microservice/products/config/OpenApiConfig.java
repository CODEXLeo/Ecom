package com.microservice.products.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {

        return new OpenAPI()

                .info(
                        new Info()
                                .title("Product Service API")
                                .description(
                                        "REST API for product catalogue "
                                        + "and inventory management. "
                                        + "Public catalogue operations "
                                        + "require no authentication. "
                                        + "Administrative operations "
                                        + "require an ADMIN JWT. "
                                        + "Inventory reservation operations "
                                        + "are internal service-to-service "
                                        + "APIs protected by mTLS."
                                )
                                .version("v1")
                                .contact(
                                        new Contact()
                                                .name(
                                                        "Microservice Product Service"
                                                )
                                )
                )

                .addServersItem(
                        new Server()
                                .url("https://localhost:8444")
                                .description("Local HTTPS server")
                )

                .components(
                        new Components()

                                /*
                                 * Browser/user authentication.
                                 */
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(
                                                        SecurityScheme.Type.HTTP
                                                )
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )

                                /*
                                 * Internal service authentication.
                                 *
                                 * mTLS itself happens during the TLS
                                 * handshake, so Swagger cannot perform
                                 * the certificate exchange through a
                                 * normal HTTP Authorization header.
                                 */
                                .addSecuritySchemes(
                                        "mtls",
                                        new SecurityScheme()
                                                .type(
                                                        SecurityScheme.Type.MUTUALTLS
                                                )
                                )
                );
    }
}