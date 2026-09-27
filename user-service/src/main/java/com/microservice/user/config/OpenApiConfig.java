package com.microservice.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/*
 * Optional OpenAPI configuration.
 *
 * Springdoc can automatically generate OpenAPI documentation from
 * the application's controllers, so this configuration is not required
 * for basic Swagger/OpenAPI functionality.
 *
 * This configuration is useful as the microservice grows because it
 * provides a central place to customize the OpenAPI specification.
 *
 * In the future, we may use it to configure:
 *
 * - API title, version and description
 * - Contact and license information
 * - Multiple API versions or API groups
 * - Server/environment information
 * - Authentication and authorization schemes
 *   (for example JWT/OAuth2 when Spring Security is added)
 * - Other global OpenAPI documentation settings
 *
 * Keeping this configuration separate also prevents API documentation
 * metadata from being scattered across controllers and makes it easier
 * to maintain as the User Service becomes more complex.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI().info(new Info().title("User Service API").version("v1").description("User authentication and account management API"));
    }
}