package com.microservice.orders.config;

import java.security.interfaces.RSAPublicKey;

import com.microservice.orders.config.properties.JwtProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtProperties jwtProperties;
    private final PemKeyLoader pemKeyLoader;

    public SecurityConfig(
            JwtProperties jwtProperties,
            PemKeyLoader pemKeyLoader) {

        this.jwtProperties = jwtProperties;
        this.pemKeyLoader = pemKeyLoader;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = jwtAuthenticationConverter();

        http

                /*
                 * ========================================================
                 * CSRF
                 * ========================================================
                 *
                 * Order Service is a stateless bearer-token API.
                 *
                 * Browser authentication uses:
                 *
                 *     Authorization: Bearer <JWT>
                 *
                 * It does not authenticate users using cookies.
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * ========================================================
                 * SESSION
                 * ========================================================
                 *
                 * No server-side HTTP session.
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * ========================================================
                 * AUTHORIZATION
                 * ========================================================
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * Swagger UI and OpenAPI documentation.
                         */
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        /*
                         * Health endpoint for monitoring/load balancers.
                         */
                        .requestMatchers(
                                "/actuator/health"
                        ).permitAll()

                        /*
                         * Order Service has no public business endpoints.
                         *
                         * Every order operation requires an authenticated
                         * user.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/orders"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/orders",
                                "/api/v1/orders/**"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/orders/{orderId}/cancel"
                        ).authenticated()

                        /*
                         * Everything else requires authentication.
                         */
                        .anyRequest().authenticated()
                )

                /*
                 * ========================================================
                 * JWT RESOURCE SERVER
                 * ========================================================
                 *
                 * JWTs are issued by User Service.
                 */
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt
                                        .decoder(jwtDecoder())
                                        .jwtAuthenticationConverter(
                                                jwtAuthenticationConverter
                                        )
                        )
                );

        return http.build();
    }

    /*
     * ================================================================
     * JWT DECODER
     * ================================================================
     */

    @Bean
    JwtDecoder jwtDecoder() {

        RSAPublicKey publicKey =
                pemKeyLoader.loadPublicKey(
                        jwtProperties.publicKeyPath()
                );

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(publicKey)
                        .build();

        /*
         * Validate:
         *
         * - signature
         * - exp
         * - nbf
         * - issuer
         */
        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer(
                        jwtProperties.issuer()
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }

    /*
     * ================================================================
     * JWT AUTHORITY MAPPING
     * ================================================================
     *
     * User Service creates:
     *
     *     "role": "ROLE_USER"
     *     "role": "ROLE_ADMIN"
     *
     * Therefore we explicitly read the "role" claim and do not add
     * another ROLE_ prefix.
     */

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");

        /*
         * The JWT already contains ROLE_USER / ROLE_ADMIN.
         *
         * Do not add another ROLE_ prefix.
         */
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return converter;
    }
}