package com.microservice.products.config;

import java.security.interfaces.RSAPublicKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.microservice.products.config.JwtProperties;

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

        JwtAuthenticationConverter
                jwtAuthenticationConverter =
                        jwtAuthenticationConverter();

        http

                /*
                 * ====================================================
                 * CSRF
                 * ====================================================
                 *
                 * Product Service does not use browser cookies
                 * for authentication.
                 *
                 * Authentication is JWT bearer based for users
                 * and X.509 based for internal services.
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * ====================================================
                 * SESSION
                 * ====================================================
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * ====================================================
                 * X.509 SERVICE AUTHENTICATION
                 * ====================================================
                 *
                 * Inventory endpoints are protected by:
                 *
                 *     mTLS
                 *     +
                 *     ROLE_SERVICE_ORDER
                 */
                .x509(x509 ->
                        x509
                                .subjectPrincipalRegex(
                                        "CN=(.*?)(?:,|$)"
                                )
                                .userDetailsService(
                                        serviceIdentityUserDetailsService()
                                )
                )

                /*
                 * ====================================================
                 * AUTHORIZATION
                 * ====================================================
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * Swagger / OpenAPI.
                         */
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        /*
                         * Public product catalogue.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).permitAll()

                        /*
                         * Inventory is internal only.
                         *
                         * Controller-level @PreAuthorize checks:
                         *
                         *     ROLE_SERVICE_ORDER
                         */
                        .requestMatchers(
                                "/api/v1/inventory/**"
                        ).authenticated()

                        /*
                         * Everything else requires authentication.
                         */
                        .anyRequest().authenticated()
                )

                /*
                 * ====================================================
                 * JWT RESOURCE SERVER
                 * ====================================================
                 *
                 * Browser/user requests continue to use JWT.
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
     * X.509 SERVICE IDENTITY
     * ================================================================
     */

    @Bean
    UserDetailsService serviceIdentityUserDetailsService() {

        return username -> {

            if ("order-service".equals(username)) {

                return User
                        .withUsername(username)
                        .password("")
                        .authorities("ROLE_SERVICE_ORDER")
                        .build();
            }

            /*
             * A certificate signed by our internal CA is not
             * sufficient by itself.
             *
             * The service identity must also be explicitly
             * registered here.
             */
            throw new IllegalArgumentException(
                    "Unknown service certificate identity: "
                            + username
            );
        };
    }

    /*
     * ================================================================
     * JWT
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

        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer(
                        jwtProperties.issuer()
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }

    private JwtAuthenticationConverter
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter
                authoritiesConverter =
                        new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName(
                "role"
        );

        /*
         * User Service already creates:
         *
         * ROLE_USER
         * ROLE_ADMIN
         *
         * Therefore no additional prefix is required.
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