package com.microservice.one.identity.dto.response;

/**
 * Authentication response returned after successful login
 * or token refresh.
 */
public record AuthResponse(

        String accessToken,

        String refreshToken,

        String tokenType,

        long expiresIn,

        long refreshExpiresIn,

        UserResponse user

) {
}