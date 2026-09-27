package com.microservice.payments.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserImpl
        implements CurrentUser {

    @Override
    public UUID userId() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication
                instanceof JwtAuthenticationToken jwt)) {

            throw new IllegalStateException(
                    "Authenticated JWT principal is required"
            );
        }

        String subject =
                jwt.getToken()
                        .getSubject();

        if (subject == null
                || subject.isBlank()) {

            throw new IllegalStateException(
                    "JWT subject is missing"
            );
        }

        try {
            return UUID.fromString(
                    subject
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT subject is not a valid UUID",
                    exception
            );
        }
    }

    @Override
    public String role() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException(
                    "Authentication is missing"
            );
        }

        return authentication
                .getAuthorities()
                .stream()
                .findFirst()
                .map(Object::toString)
                .orElse(null);
    }

    @Override
    public boolean isAdmin() {
        return authenticationHas(
                "ROLE_ADMIN"
        );
    }

    private boolean authenticationHas(
            String authority
    ) {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(
                                granted ->
                                        authority.equals(
                                                granted.getAuthority()
                                        )
                        );
    }
}