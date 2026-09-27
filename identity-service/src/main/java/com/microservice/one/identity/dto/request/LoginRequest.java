package com.microservice.one.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for user login.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Device ID is required")
        @Size(max = 100, message = "Device ID must not exceed 100 characters")
        String deviceId

) {
}