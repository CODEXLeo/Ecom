package com.microservice.one.identity.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.exception.UserNotFoundException;
import com.microservice.one.identity.mapper.UserMapper;
import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.security.CustomUserDetails;
import com.microservice.one.identity.service.UserService;

@Service
@Transactional
public class UserServiceImpl
        implements UserService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    UserServiceImpl.class);

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    public UserServiceImpl(

            UserRepository userRepository,

            UserMapper userMapper) {

        this.userRepository =
                userRepository;

        this.userMapper =
                userMapper;
    }

    /*
     * ============================================================
     * CURRENT USER
     * ============================================================
     */

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {

        User user =
                getAuthenticatedUser();

        LOGGER.info(
                "Fetching profile for {}",
                user.getEmail());

        return userMapper.toUserResponse(
                user);
    }

    /*
     * ============================================================
     * GET USER BY ID
     * ============================================================
     */

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(
            UUID userId) {

        LOGGER.info(
                "Fetching user {}",
                userId);

        User user =
                userRepository.findById(
                        userId)

                        .orElseThrow(
                                () ->
                                        new UserNotFoundException(
                                                "User not found with id: "
                                                        + userId));

        return userMapper.toUserResponse(
                user);
    }

    /*
     * ============================================================
     * UPDATE CURRENT USER
     * ============================================================
     */

    @Override
    public UserResponse updateCurrentUser(

            UpdateUserRequest updateUserRequest) {

        User user =
                getAuthenticatedUser();

        LOGGER.info(
                "Updating profile for {}",
                user.getEmail());

        userMapper.updateUser(
                user,
                updateUserRequest);

        User updatedUser =
                userRepository.save(
                        user);

        LOGGER.info(
                "Profile updated successfully for {}",
                updatedUser.getEmail());

        return userMapper.toUserResponse(
                updatedUser);
    }

    /*
     * ============================================================
     * DELETE CURRENT USER
     * ============================================================
     */

    @Override
    public void deleteCurrentUser() {

        User user =
                getAuthenticatedUser();

        LOGGER.info(
                "Deleting account {}",
                user.getEmail());

        userRepository.delete(
                user);

        LOGGER.info(
                "Account deleted successfully {}",
                user.getEmail());
    }

    /*
     * ============================================================
     * INTERNAL HELPER
     * ============================================================
     */

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CustomUserDetails customUserDetails)) {

            throw new UserNotFoundException(
                    "Authenticated user not found.");
        }

        return userRepository.findById(
                customUserDetails.getUserId())

                .orElseThrow(
                        () ->
                                new UserNotFoundException(
                                        "Authenticated user no longer exists."));
    }
}