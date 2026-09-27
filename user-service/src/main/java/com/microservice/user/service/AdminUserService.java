package com.microservice.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.user.dto.response.AdminUserResponse;
import com.microservice.user.entity.User;
import com.microservice.user.exception.UserNotFoundException;
import com.microservice.user.repository.UserRepository;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;

    public AdminUserService(UserRepository userRepository, RefreshSessionService refreshSessionService) {
        this.userRepository = userRepository;
        this.refreshSessionService = refreshSessionService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findUser(UUID userId) {
        User user = findUserEntity(userId);
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse setUserEnabled(UUID userId, boolean enabled) {
        User user = findUserEntityForUpdate(userId);
        if (enabled) {
            user.enable();
        } else {
            user.disable();

            /*
             * A disabled account must not retain active
             * refresh sessions.
             */
            refreshSessionService.revokeAllUserSessions(userId);
        }
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse unlockUser(UUID userId) {
        User user = findUserEntityForUpdate(userId);
        user.resetFailedLoginAttempts();
        return toResponse(user);
    }

    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        findUserEntityForUpdate(userId);
        refreshSessionService.revokeAllUserSessions(userId);
    }

    private User findUserEntity(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private User findUserEntityForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    }

    private AdminUserResponse toResponse(User user) {

        return new AdminUserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isAccountLocked(),
                user.getFailedLoginAttempts(),
                user.getLockedAt(),
                user.getLockExpiresAt());
    }
}