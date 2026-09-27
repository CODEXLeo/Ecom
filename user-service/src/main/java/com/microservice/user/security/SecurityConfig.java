package com.microservice.user.security;

import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, BearerTokenResolver bearerTokenResolver, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        /*
         * =========================================================
         * CSRF PROTECTION
         * =========================================================
         *
         * Authentication uses:
         *
         *     __Host-access_token
         *     __Host-refresh_token
         *
         * These are cookies, therefore the browser automatically
         * sends them with requests.
         *
         * Cookie-based authentication requires CSRF protection.
         * Spring's documentation explicitly says HttpOnly=false is necessary when a JavaScript framework needs to read the CSRF cookie.
         * So JavaScript can read the non-HttpOnly CSRF cookie by document.cookie
         */
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        /*
         * Spring Security SPA CSRF handler.
         *
         * The XSRF-TOKEN cookie contains the raw CSRF token.
         *
         * The frontend reads it and sends:
         *
         *     X-XSRF-TOKEN: <raw-token>
         */

        CsrfTokenRequestHandler csrfTokenRequestHandler = new SpaCsrfTokenRequestHandler();

        httpSecurity

                /*
                 * =================================================
                 * CSRF
                 * =================================================
                 */
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(csrfTokenRequestHandler)

                        /*
                         * IMPORTANT
                         * -------------------------------------------------
                         *
                         * Spring Security's OAuth2 Resource Server
                         * automatically adds its BearerTokenRequestMatcher
                         * to the CSRF ignore matchers.
                         *
                         * That is normally useful for APIs using:
                         *
                         *     Authorization: Bearer <token>
                         *
                         * but it is NOT correct for our architecture
                         * because our bearer token is stored in a cookie.
                         *
                         * The browser automatically sends that cookie,
                         * so cookie authentication remains vulnerable
                         * to CSRF.
                         *
                         * Therefore we directly replace the matcher
                         * inside the actual CsrfFilter using an
                         * ObjectPostProcessor.
                         *
                         * This happens after the OAuth2 Resource Server
                         * configuration has been applied.
                         */

                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                                    @Override
                                    public <O extends CsrfFilter> O postProcess(O object) {
                                        object.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                        return object;
                                    }
                                }
                        )
                )

                /*
                 * =================================================
                 * SESSION MANAGEMENT
                 * =================================================
                 *
                 * Authentication is stateless.
                 *
                 * No HTTP session is used for authentication.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                /*
                 * =================================================
                 * AUTHORIZATION
                 * =================================================
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * Authentication endpoints.
                         *
                         * They are publicly accessible, but unsafe
                         * methods still require CSRF.
                         */
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        /*
                         * CSRF bootstrap endpoint.
                         *
                         * GET is safe.
                         */
                        .requestMatchers("/api/v1/auth/csrf").permitAll()

                        /*
                         * Swagger/OpenAPI.
                         */
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        /*
                         * Error dispatch.
                         */
                        .requestMatchers("/error").permitAll()

                        /*
                         * Everything else requires authentication.
                         */
                        .anyRequest().authenticated()
                )

                /*
                 * =================================================
                 * OAUTH2 RESOURCE SERVER / JWT
                 * =================================================
                 *
                 * Supports both:
                 *
                 * 1. Authorization: Bearer <token>
                 *
                 * 2. __Host-access_token cookie
                 *
                 * CookieBearerTokenResolver handles the second case.
                 */
                .oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(bearerTokenResolver).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return httpSecurity.build();
    }

    /*
     * =============================================================
     * SPA CSRF TOKEN REQUEST HANDLER
     * =============================================================
     *
     * Cookie:
     *
     *     XSRF-TOKEN
     *
     * Header:
     *
     *     X-XSRF-TOKEN
     *
     * Authentication cookies remain HttpOnly.
     */

    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestAttributeHandler plain = new CsrfTokenRequestAttributeHandler();
        private final XorCsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();
        SpaCsrfTokenRequestHandler() {

            /*
             * Do not expose the XOR token through the request
             * attribute name.
             */
            this.xor.setCsrfRequestAttributeName(null);
        }

        @Override
        public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Supplier<CsrfToken> csrfToken) {

            /*
             * Use Spring's XOR handler.
             */
            this.xor.handle(httpServletRequest, httpServletResponse, csrfToken);

            /*
             * Force token generation so that the
             * XSRF-TOKEN cookie is issued.
             */
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest httpServletRequest, CsrfToken csrfToken) {

            /*
             * SPA request:
             *
             * The frontend sends the raw token through
             * X-XSRF-TOKEN.
             */
            String headerValue = httpServletRequest.getHeader(csrfToken.getHeaderName());

            if (headerValue != null && !headerValue.isBlank()) {
                return this.plain.resolveCsrfTokenValue(httpServletRequest, csrfToken);
            }

            /*
             * Fall back to the XOR resolver.
             */
            return this.xor.resolveCsrfTokenValue(httpServletRequest, csrfToken);
        }
    }

    /*
     * =============================================================
     * JWT -> SPRING AUTHORITIES
     * =============================================================
     *
     * JWT:
     *
     *     "role": "ROLE_USER"
     *
     * or:
     *
     *     "role": "ROLE_ADMIN"
     *
     * Since ROLE_ is already present, Spring must not add another
     * prefix.
     */

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        /*
         * Read authorities from the "role" claim.
         */
        authoritiesConverter.setAuthoritiesClaimName("role");

        /*
         * Do not prepend another prefix.
         */
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return authenticationConverter;
    }
}