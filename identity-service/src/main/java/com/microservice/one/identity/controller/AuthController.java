package com.microservice.one.identity.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.annotation.Validated;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.one.identity.dto.request.LoginRequest;
import com.microservice.one.identity.dto.request.RefreshTokenRequest;
import com.microservice.one.identity.dto.request.RegisterRequest;
import com.microservice.one.identity.dto.response.AuthResponse;
import com.microservice.one.identity.dto.response.RegisterResponse;
import com.microservice.one.identity.service.AuthenticationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(
        name = "Authentication",
        description =
                "Authentication APIs for user registration, "
                        + "login, logout and token refresh")
public class AuthController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AuthController.class);

    private final AuthenticationService authenticationService;

    public AuthController(
            AuthenticationService authenticationService) {

        this.authenticationService =
                authenticationService;
    }

    /*
     * ============================================================
     * REGISTER
     * ============================================================
     */

    @Operation(summary = "Register a new user")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "User registered successfully"),

        @ApiResponse(
                responseCode = "400",
                description = "Validation failed"),

        @ApiResponse(
                responseCode = "409",
                description = "User already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(

            @Valid
            @RequestBody
            RegisterRequest registerRequest) {

        LOGGER.info("Register endpoint invoked for {}", registerRequest.email());
        RegisterResponse registerResponse = authenticationService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(registerResponse);
    }

    /*
     * ============================================================
     * LOGIN
     * ============================================================
     */

    @Operation(
            summary = "Authenticate user")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Authentication successful"),

        @ApiResponse(
                responseCode = "401",
                description =
                        "Invalid email or password")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(

            @Valid
            @RequestBody
            LoginRequest loginRequest) {

        LOGGER.info(
                "Login endpoint invoked for {}",
                loginRequest.email());

        AuthResponse authResponse =
                authenticationService.login(
                        loginRequest);

        return ResponseEntity.ok(
                authResponse);
    }

    /*
     * ============================================================
     * LOGOUT
     * ============================================================
     */

    @Operation(
            summary = "Logout user")
    @ApiResponses({
        @ApiResponse(
                responseCode = "204",
                description =
                        "Logout successful"),

        @ApiResponse(
                responseCode = "401",
                description =
                        "Refresh token belongs to another device")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(

            @Valid
            @RequestBody
            RefreshTokenRequest refreshTokenRequest) {

        LOGGER.info(
                "Logout endpoint invoked.");

        authenticationService.logout(
                refreshTokenRequest);

        return ResponseEntity
                .noContent()
                .build();
    }

    /*
     * ============================================================
     * REFRESH TOKEN
     * ============================================================
     */

    @Operation(
            summary = "Refresh access token")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Token refreshed"),

        @ApiResponse(
                responseCode = "401",
                description =
                        "Invalid refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(

            @Valid
            @RequestBody
            RefreshTokenRequest refreshTokenRequest) {

        LOGGER.info(
                "Refresh token endpoint invoked.");

        AuthResponse authResponse =
                authenticationService.refreshToken(
                        refreshTokenRequest);

        return ResponseEntity.ok(
                authResponse);
    }
}