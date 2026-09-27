package com.microservice.user.controller;

import com.microservice.user.dto.response.AdminUserResponse;
import com.microservice.user.service.AdminUserService;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> getUsers() {

        return ResponseEntity.ok(
                adminUserService.findAllUsers());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(
            @PathVariable UUID userId) {

        return ResponseEntity.ok(
                adminUserService.findUser(userId));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<AdminUserResponse> setUserStatus(
            @PathVariable UUID userId,
            @RequestParam @NotNull Boolean enabled) {

        return ResponseEntity.ok(
                adminUserService.setUserEnabled(
                        userId,
                        enabled));
    }

    @PostMapping("/{userId}/unlock")
    public ResponseEntity<AdminUserResponse> unlockUser(
            @PathVariable UUID userId) {

        return ResponseEntity.ok(
                adminUserService.unlockUser(userId));
    }

    @PostMapping("/{userId}/sessions/revoke")
    public ResponseEntity<Void> revokeAllSessions(
            @PathVariable UUID userId) {

        adminUserService.revokeAllUserSessions(userId);

        return ResponseEntity.noContent().build();
    }
}