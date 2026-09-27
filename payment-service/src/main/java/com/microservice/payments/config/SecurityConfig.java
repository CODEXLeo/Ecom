package com.microservice.payments.config;

import java.security.interfaces.RSAPublicKey;

import com.microservice.payments.config.properties.JwtProperties;
import com.microservice.payments.security.ServiceIdentityUserDetailsService;

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

    private final ServiceIdentityUserDetailsService
            serviceIdentityUserDetailsService;

    public SecurityConfig(
            JwtProperties jwtProperties,
            PemKeyLoader pemKeyLoader,
            ServiceIdentityUserDetailsService
                    serviceIdentityUserDetailsService
    ) {
        this.jwtProperties =
                jwtProperties;

        this.pemKeyLoader =
                pemKeyLoader;

        this.serviceIdentityUserDetailsService =
                serviceIdentityUserDetailsService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf ->
                        csrf.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth ->
                        auth

                                .requestMatchers(
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml"
                                )
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/actuator/health"
                                )
                                .permitAll()

                                /*
                                 * Service-to-service payment commands.
                                 */
                                .requestMatchers(
                                        "/api/v1/payments/internal/**"
                                )
                                .hasRole(
                                        "SERVICE_ORDER"
                                )

                                /*
                                 * Browser/user payment read access.
                                 */
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/payments/**"
                                )
                                .authenticated()

                                .anyRequest()
                                .authenticated()
                )

                /*
                 * JWT authentication for browser/user requests.
                 */
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt
                                                        .decoder(
                                                                jwtDecoder()
                                                        )
                                                        .jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter()
                                                        )
                                )
                )

                /*
                 * X.509 authentication for service requests.
                 *
                 * The certificate CN becomes:
                 *
                 *     order-service
                 *
                 * and is resolved by the dedicated
                 * ServiceIdentityUserDetailsService.
                 */
                .x509(
                        x509 ->
                                x509
                                        .subjectPrincipalRegex(
                                                "CN=(.*?)(?:,|$)"
                                        )
                                        .userDetailsService(
                                                serviceIdentityUserDetailsService
                                        )
                );

        return http.build();
    }

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
                JwtValidators
                        .createDefaultWithIssuer(
                                jwtProperties.issuer()
                        );

        decoder.setJwtValidator(
                validator
        );

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter
                authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter
                .setAuthoritiesClaimName(
                        "role"
                );

        authoritiesConverter
                .setAuthorityPrefix("");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return converter;
    }
}