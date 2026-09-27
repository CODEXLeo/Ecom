package com.microservice.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user.dto.request.LoginRequest;
import com.microservice.user.dto.request.RegisterRequest;
import com.microservice.user.dto.response.LoginResponse;
import com.microservice.user.dto.response.RegisterResponse;
import com.microservice.user.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration and authentication APIs")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Create a new user account")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        RegisterResponse registerResponse = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(registerResponse);
    }

    @Operation(summary = "Login existing user", description = "Authenticate user and establish HttpOnly authentication cookies")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        LoginResponse loginResponse = authService.login(loginRequest, httpServletRequest, httpServletResponse);
        return ResponseEntity.ok(loginResponse);
    }

    @Operation(summary = "Refresh authentication", description = "Rotate the refresh token and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authService.refresh(httpServletRequest, httpServletResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Logout", description = "Revoke the refresh session and clear authentication cookies")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        authService.logout(httpServletRequest, httpServletResponse);
        return ResponseEntity.noContent().build();
    }
}