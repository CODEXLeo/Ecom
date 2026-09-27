package com.microservice.one.identity.service;

import java.util.UUID;

import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.dto.response.UserResponse;

public interface UserService {

    /**
     * Returns the currently authenticated user.
     *
     * @return authenticated user's profile
     */
    UserResponse getCurrentUser();

    /**
     * Returns a user by ID.
     *
     * Intended mainly for internal service usage.
     *
     * @param userId user id
     * @return user profile
     */
    UserResponse getUserById(
            UUID userId);

    /**
     * Updates the authenticated user's profile.
     *
     * @param updateUserRequest update request
     * @return updated profile
     */
    UserResponse updateCurrentUser(
            UpdateUserRequest updateUserRequest);

    /**
     * Deletes the authenticated user's account.
     */
    void deleteCurrentUser();
}