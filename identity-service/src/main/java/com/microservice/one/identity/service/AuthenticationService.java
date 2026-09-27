package com.microservice.one.identity.service;

import com.microservice.one.identity.dto.request.LoginRequest;
import com.microservice.one.identity.dto.request.RefreshTokenRequest;
import com.microservice.one.identity.dto.request.RegisterRequest;
import com.microservice.one.identity.dto.response.AuthResponse;
import com.microservice.one.identity.dto.response.RegisterResponse;

public interface AuthenticationService {

    /**
     * Registers a new user.
     *
     * @param registerRequest registration request
     * @return registration response
     */
    RegisterResponse register(RegisterRequest registerRequest);

    /**
     * Authenticates an existing user and creates
     * an access token and device-bound refresh token.
     *
     * @param loginRequest login request
     * @return authentication response
     */
    AuthResponse login(LoginRequest loginRequest);

    /**
     * Rotates a valid refresh token and generates
     * a new access token and refresh token.
     *
     * @param refreshTokenRequest refresh token request
     * @return authentication response containing
     *         the rotated tokens
     */
    AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest);

    /**
     * Revokes the supplied device-bound refresh token.
     *
     * @param refreshTokenRequest refresh token request
     */
    void logout(RefreshTokenRequest refreshTokenRequest);
}