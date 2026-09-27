package com.microservice.one.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating the authenticated user's profile.
 */
public record UpdateUserRequest(

        @NotBlank(message = "First name is required")
        @Size(
                min = 2,
                max = 100,
                message = "First name must be between 2 and 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(
                min = 2,
                max = 100,
                message = "Last name must be between 2 and 100 characters")
        String lastName

) {
}