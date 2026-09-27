package com.microservice.user.service;

import com.microservice.user.dto.request.ChangePasswordRequest;
import com.microservice.user.dto.request.UpdateProfileRequest;
import com.microservice.user.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {
    UserResponse getCurrentUser(UUID userId);
    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);
    void changePassword(UUID userId, ChangePasswordRequest request);
}
