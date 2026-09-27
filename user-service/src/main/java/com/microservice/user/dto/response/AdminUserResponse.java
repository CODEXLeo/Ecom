package com.microservice.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.microservice.user.enums.Role;

public record AdminUserResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean enabled,
        boolean accountLocked,
        int failedLoginAttempts,
        Instant lockedAt,
        Instant lockExpiresAt
) {
}