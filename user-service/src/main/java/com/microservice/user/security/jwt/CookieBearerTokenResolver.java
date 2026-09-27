package com.microservice.user.security.jwt;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import com.microservice.user.security.cookie.AuthCookieProperties;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

    private final AuthCookieProperties cookieProperties;

    public CookieBearerTokenResolver(AuthCookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    @Override
    public String resolve(HttpServletRequest request) {

        /*
         * Authorization: Bearer <token> remains supported.
         *
         * This is useful for:
         * - service-to-service requests
         * - Postman
         * - curl
         * - future API Gateway
         */
        String bearerToken = defaultResolver.resolve(request);

        if (bearerToken != null) {
            return bearerToken;
        }

        /*
         * Browser authentication uses the HttpOnly cookie.
         */
        if (request.getCookies() == null) {
            return null;
        }

        for (var cookie : request.getCookies()) {
            if (cookieProperties.getAccessTokenName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}