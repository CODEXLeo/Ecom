package com.microservice.orders.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserImpl implements CurrentUser {

    @Override
    public UUID userId() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException(
                    "Authenticated JWT principal is required"
            );
        }

        String subject =
                jwtAuthentication
                        .getToken()
                        .getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException(
                    "JWT subject is missing"
            );
        }

        try {
            return UUID.fromString(subject);
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

        return authentication
                .getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority ->
                        authority.startsWith("ROLE_")
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user role is missing"
                        )
                );
    }

    @Override
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role());
    }
}