package com.microservice.user.dto.response;

import java.util.UUID;
import com.microservice.user.enums.Role;

public record UserResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        Role role
) {
}
