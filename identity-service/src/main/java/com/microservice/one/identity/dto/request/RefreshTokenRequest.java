package com.microservice.one.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for refreshing an access token.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken,

        @NotBlank(message = "Device ID is required")
        String deviceId

) {
}