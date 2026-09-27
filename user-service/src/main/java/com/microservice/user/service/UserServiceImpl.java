package com.microservice.user.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.user.dto.request.ChangePasswordRequest;
import com.microservice.user.dto.request.UpdateProfileRequest;
import com.microservice.user.dto.response.UserResponse;
import com.microservice.user.entity.User;
import com.microservice.user.exception.InvalidCurrentPasswordException;
import com.microservice.user.exception.PasswordReuseException;
import com.microservice.user.exception.UserNotFoundException;
import com.microservice.user.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshSessionService refreshSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessionService = refreshSessionService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = findUser(userId);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest upaProfileRequest) {
        User user = findUser(userId);
        user.updateProfile(upaProfileRequest.firstName(), upaProfileRequest.lastName());
        return toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(changePasswordRequest.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        if (passwordEncoder.matches(changePasswordRequest.newPassword(), user.getPasswordHash())) {
            throw new PasswordReuseException();
        }

        user.changePassword(passwordEncoder.encode(changePasswordRequest.newPassword()));

        /*
         * Password changes invalidate every existing
         * refresh session.
         *
         * This forces all other devices to authenticate again.
         */
        refreshSessionService.revokeAllUserSessions(userId);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole());
    }
}