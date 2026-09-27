package com.microservice.one.identity.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.annotation.Validated;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Operations for the authenticated user")
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
     * ============================================================
     * GET CURRENT USER
     * ============================================================
     */
    @Operation(summary = "Get current authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        LOGGER.info("Fetching authenticated user profile.");
        UserResponse userResponse = userService.getCurrentUser();
        return ResponseEntity.ok(userResponse);
    }

    /*
     * ============================================================
     * UPDATE CURRENT USER
     * ============================================================
     */
    @Operation(summary = "Update current authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401",  description = "Unauthorized")
    })
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(

            @Valid
            @RequestBody
            UpdateUserRequest updateUserRequest) {

        LOGGER.info("Updating authenticated user profile.");
        UserResponse userResponse = userService.updateCurrentUser(updateUserRequest);
        return ResponseEntity.ok(userResponse);
    }

    /*
     * ============================================================
     * DELETE CURRENT USER
     * ============================================================
     */

    @Operation(summary = "Delete current authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted successfully"),
        @ApiResponse(responseCode = "401",description = "Unauthorized")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser() {
        LOGGER.info("Deleting authenticated user account.");
        userService.deleteCurrentUser();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}