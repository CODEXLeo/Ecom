package com.microservice.gateway.security;

import java.security.interfaces.RSAPublicKey;

import com.microservice.gateway.config.GatewaySecurityProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMethodSecurity
public class GatewaySecurityConfig {

    private final GatewaySecurityProperties properties;

    private final PemKeyLoader pemKeyLoader;

    public GatewaySecurityConfig(
            GatewaySecurityProperties properties,
            PemKeyLoader pemKeyLoader
    ) {
        this.properties = properties;
        this.pemKeyLoader = pemKeyLoader;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ServerBearerTokenAuthenticationConverter bearerTokenConverter
    ) {

        return http

                /*
                 * ============================================================
                 * GATEWAY SECURITY MODEL
                 * ============================================================
                 *
                 * The Gateway itself is stateless.
                 *
                 * User Service owns:
                 *
                 * - authentication
                 * - refresh token handling
                 * - logout
                 * - CSRF
                 *
                 * Product / Order / Payment use bearer authentication.
                 */

                .csrf(
                        ServerHttpSecurity.CsrfSpec::disable
                )

                .httpBasic(
                        ServerHttpSecurity.HttpBasicSpec::disable
                )

                .formLogin(
                        ServerHttpSecurity.FormLoginSpec::disable
                )

                .logout(
                        ServerHttpSecurity.LogoutSpec::disable
                )

                /*
                 * ============================================================
                 * AUTHORIZATION
                 * ============================================================
                 */

                .authorizeExchange(authorize -> authorize

                        /*
                         * ====================================================
                         * GATEWAY HEALTH
                         * ====================================================
                         */

                        .pathMatchers(
                                "/actuator/health"
                        ).permitAll()

                        /*
                         * ====================================================
                         * AUTHENTICATION ENDPOINTS
                         * ====================================================
                         *
                         * User Service owns authentication.
                         *
                         * These must reach User Service without requiring
                         * an already authenticated Gateway request.
                         */

                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/csrf"
                        ).permitAll()

                        /*
                         * ====================================================
                         * PUBLIC PRODUCT CATALOGUE
                         * ====================================================
                         */

                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).permitAll()

                        /*
                         * ====================================================
                         * USER SELF SERVICE
                         * ====================================================
                         */

                        .pathMatchers(
                                "/api/v1/users/**"
                        ).authenticated()

                        /*
                         * ====================================================
                         * USER ADMINISTRATION
                         * ====================================================
                         */

                        .pathMatchers(
                                "/api/v1/admin/**"
                        ).hasRole("ADMIN")

                        /*
                         * ====================================================
                         * PRODUCT ADMINISTRATION
                         * ====================================================
                         */

                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/products"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        /*
                         * ====================================================
                         * CUSTOMER ORDERS
                         * ====================================================
                         */

                        .pathMatchers(
                                "/api/v1/orders/**"
                        ).authenticated()

                        /*
                         * ====================================================
                         * CUSTOMER PAYMENT LOOKUP
                         * ====================================================
                         *
                         * Only:
                         *
                         * GET /api/v1/payments/{paymentId}
                         *
                         * is publicly routable through the Gateway.
                         */

                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/payments/*"
                        ).authenticated()

                        /*
                         * ====================================================
                         * INTERNAL APIs
                         * ====================================================
                         *
                         * These must NEVER be exposed through the public
                         * Gateway.
                         *
                         * Order -> Product inventory APIs remain protected
                         * by service-to-service mTLS.
                         *
                         * Order -> Payment internal APIs remain protected
                         * by service-to-service mTLS.
                         */

                        .pathMatchers(
                                "/api/v1/inventory/**"
                        ).denyAll()

                        .pathMatchers(
                                "/api/v1/payments/internal/**"
                        ).denyAll()

                        /*
                         * ====================================================
                         * DEFAULT DENY
                         * ====================================================
                         */

                        .anyExchange()
                        .denyAll()
                )

                /*
                 * ============================================================
                 * JWT RESOURCE SERVER
                 * ============================================================
                 */

                .oauth2ResourceServer(
                        oauth2 -> oauth2

                                .jwt(
                                        jwt -> jwt
                                                .jwtDecoder(
                                                        jwtDecoder()
                                                )
                                                .jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter()
                                                )
                                )

                                /*
                                 * Accept both:
                                 *
                                 * Authorization: Bearer <JWT>
                                 *
                                 * and:
                                 *
                                 * __Host-access_token=<JWT>
                                 */

                                .bearerTokenConverter(
                                        bearerTokenConverter
                                )
                )

                .build();
    }

    /*
     * ================================================================
     * REACTIVE JWT DECODER
     * ================================================================
     *
     * The Gateway uses Spring WebFlux.
     *
     * Therefore ServerHttpSecurity requires:
     *
     *     ReactiveJwtDecoder
     *
     * NOT:
     *
     *     JwtDecoder
     *
     * User Service signs JWTs with its RSA private key.
     * Gateway validates them using User Service's RSA public key.
     */

    @Bean
    ReactiveJwtDecoder jwtDecoder() {

        RSAPublicKey publicKey =
                pemKeyLoader.loadPublicKey(
                        properties.publicKeyPath()
                );

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder
                        .withPublicKey(publicKey)
                        .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        properties.issuer()
                )
        );

        return decoder;
    }

    /*
     * ================================================================
     * JWT AUTHORITIES
     * ================================================================
     *
     * User Service already puts:
     *
     *     ROLE_USER
     *     ROLE_ADMIN
     *
     * into the "role" claim.
     *
     * Therefore the Gateway must NOT prepend another "ROLE_".
     */

    @Bean
    Converter<
            Jwt,
            Mono<AbstractAuthenticationToken>
            > jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName(
                "role"
        );

        authoritiesConverter.setAuthorityPrefix(
                ""
        );

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return new ReactiveJwtAuthenticationConverterAdapter(
                converter
        );
    }

    /*
     * ================================================================
     * COOKIE + BEARER TOKEN RESOLUTION
     * ================================================================
     *
     * Existing User Service authentication uses:
     *
     *     __Host-access_token
     *
     * The Gateway accepts the same browser credential.
     *
     * Explicit Authorization header always wins.
     */

    @Bean
    ServerBearerTokenAuthenticationConverter
    bearerTokenAuthenticationConverter() {

        return new ServerBearerTokenAuthenticationConverter() {

            @Override
            public Mono<
                    org.springframework.security.core.Authentication
                    > convert(
                    ServerWebExchange exchange
            ) {

                /*
                 * First allow the normal:
                 *
                 * Authorization: Bearer <JWT>
                 */

                return super.convert(exchange)

                        .switchIfEmpty(
                                Mono.defer(() -> {

                                    var cookie =
                                            exchange
                                                    .getRequest()
                                                    .getCookies()
                                                    .getFirst(
                                                            properties
                                                                    .accessTokenCookieName()
                                                    );

                                    if (
                                            cookie == null
                                                    || cookie.getValue().isBlank()
                                    ) {
                                        return Mono.empty();
                                    }

                                    return Mono.just(
                                            new BearerTokenAuthenticationToken(
                                                    cookie.getValue()
                                            )
                                    );
                                })
                        );
            }
        };
    }
}