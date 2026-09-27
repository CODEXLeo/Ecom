package com.microservice.one.identity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.microservice.one.identity.enums.Role;

/**
 * User information returned to the client.
 */
public record UserResponse(

        UUID userId,

        String firstName,

        String lastName,

        String email,

        Role role,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}