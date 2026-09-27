package com.microservice.one.identity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.microservice.one.identity.enums.Role;

/**
 * Response returned after successful user registration.
 */
public record RegisterResponse(

        UUID userId,

        String firstName,

        String lastName,

        String email,

        Role role,

        LocalDateTime createdAt,

        String message

) {
}