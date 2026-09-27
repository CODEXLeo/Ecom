package com.microservice.one.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.enums.Role;
import com.microservice.one.identity.exception.UserNotFoundException;
import com.microservice.one.identity.mapper.UserMapper;
import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.security.CustomUserDetails;
import com.microservice.one.identity.service.impl.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {

        user = createUser();

        userResponse = new UserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // GET CURRENT USER
    // ============================================================

    @Test
    void getCurrentUser_shouldReturnAuthenticatedUser() {

        authenticateUser(user);

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.of(user));

        when(userMapper.toUserResponse(user))
                .thenReturn(userResponse);

        UserResponse result =
                userService.getCurrentUser();

        assertThat(result)
                .isEqualTo(userResponse);

        assertThat(result.userId())
                .isEqualTo(user.getUserId());

        assertThat(result.firstName())
                .isEqualTo("Swatantra");

        assertThat(result.lastName())
                .isEqualTo("Naskar");

        assertThat(result.email())
                .isEqualTo("swata@example.com");

        assertThat(result.role())
                .isEqualTo(Role.ROLE_USER);

        verify(userRepository)
                .findById(user.getUserId());

        verify(userMapper)
                .toUserResponse(user);
    }

    @Test
    void getCurrentUser_shouldThrowWhenAuthenticationIsMissing() {

        SecurityContextHolder.clearContext();

        assertThatThrownBy(
                () -> userService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Authenticated user not found.");

        verifyNoInteractions(
                userRepository,
                userMapper);
    }

    @Test
    void getCurrentUser_shouldThrowWhenAuthenticatedUserNoLongerExists() {

        authenticateUser(user);

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage(
                        "Authenticated user no longer exists.");

        verify(userRepository)
                .findById(user.getUserId());

        verifyNoInteractions(userMapper);
    }

    @Test
    void getCurrentUser_shouldThrowWhenPrincipalIsNotCustomUserDetails() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "anonymous",
                        null);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThatThrownBy(
                () -> userService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Authenticated user not found.");

        verifyNoInteractions(
                userRepository,
                userMapper);
    }

    // ============================================================
    // GET USER BY ID
    // ============================================================

    @Test
    void getUserById_shouldReturnUser() {

        UUID userId =
                user.getUserId();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(userMapper.toUserResponse(user))
                .thenReturn(userResponse);

        UserResponse result =
                userService.getUserById(userId);

        assertThat(result)
                .isEqualTo(userResponse);

        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(result.firstName())
                .isEqualTo("Swatantra");

        assertThat(result.lastName())
                .isEqualTo("Naskar");

        assertThat(result.email())
                .isEqualTo("swata@example.com");

        assertThat(result.role())
                .isEqualTo(Role.ROLE_USER);

        verify(userRepository)
                .findById(userId);

        verify(userMapper)
                .toUserResponse(user);
    }

    @Test
    void getUserById_shouldThrowWhenUserDoesNotExist() {

        UUID userId =
                UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.getUserById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage(
                        "User not found with id: "
                                + userId);

        verify(userRepository)
                .findById(userId);

        verifyNoInteractions(userMapper);
    }

    // ============================================================
    // UPDATE CURRENT USER
    // ============================================================

    @Test
    void updateCurrentUser_shouldUpdateAuthenticatedUser() {

        authenticateUser(user);

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "UpdatedFirstName",
                        "UpdatedLastName");

        User updatedUser =
                createUser();

        updatedUser.setFirstName(
                "UpdatedFirstName");

        updatedUser.setLastName(
                "UpdatedLastName");

        UserResponse updatedResponse =
                new UserResponse(
                        updatedUser.getUserId(),
                        updatedUser.getFirstName(),
                        updatedUser.getLastName(),
                        updatedUser.getEmail(),
                        updatedUser.getRole(),
                        updatedUser.getCreatedAt(),
                        updatedUser.getUpdatedAt());

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(updatedUser);

        when(userMapper.toUserResponse(updatedUser))
                .thenReturn(updatedResponse);

        UserResponse result =
                userService.updateCurrentUser(request);

        verify(userRepository)
                .findById(user.getUserId());

        verify(userMapper)
                .updateUser(user, request);

        verify(userRepository)
                .save(user);

        verify(userMapper)
                .toUserResponse(updatedUser);

        assertThat(result)
                .isEqualTo(updatedResponse);
    }

    @Test
    void updateCurrentUser_shouldThrowWhenAuthenticationIsMissing() {

        SecurityContextHolder.clearContext();

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "UpdatedFirstName",
                        "UpdatedLastName");

        assertThatThrownBy(
                () -> userService.updateCurrentUser(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Authenticated user not found.");

        verifyNoInteractions(
                userRepository,
                userMapper);
    }

    @Test
    void updateCurrentUser_shouldThrowWhenAuthenticatedUserNoLongerExists() {

        authenticateUser(user);

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.empty());

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "UpdatedFirstName",
                        "UpdatedLastName");

        assertThatThrownBy(
                () -> userService.updateCurrentUser(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage(
                        "Authenticated user no longer exists.");

        verify(userRepository)
                .findById(user.getUserId());

        verifyNoInteractions(userMapper);
    }

    // ============================================================
    // DELETE CURRENT USER
    // ============================================================

    @Test
    void deleteCurrentUser_shouldDeleteAuthenticatedUser() {

        authenticateUser(user);

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.of(user));

        userService.deleteCurrentUser();

        verify(userRepository)
                .findById(user.getUserId());

        verify(userRepository)
                .delete(user);

        verifyNoInteractions(userMapper);
    }

    @Test
    void deleteCurrentUser_shouldThrowWhenAuthenticationIsMissing() {

        SecurityContextHolder.clearContext();

        assertThatThrownBy(
                () -> userService.deleteCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Authenticated user not found.");

        verifyNoInteractions(
                userRepository,
                userMapper);
    }

    @Test
    void deleteCurrentUser_shouldThrowWhenAuthenticatedUserNoLongerExists() {

        authenticateUser(user);

        when(userRepository.findById(
                user.getUserId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.deleteCurrentUser())
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage(
                        "Authenticated user no longer exists.");

        verify(userRepository)
                .findById(user.getUserId());

        verifyNoInteractions(userMapper);
    }

    // ============================================================
    // TEST HELPERS
    // ============================================================

    private void authenticateUser(User user) {

        CustomUserDetails userDetails =
                new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private User createUser() {

        User user = new User();

        user.setUserId(
                UUID.randomUUID());

        user.setFirstName(
                "Swatantra");

        user.setLastName(
                "Naskar");

        user.setEmail(
                "swata@example.com");

        user.setPasswordHash(
                "hashed-password");

        user.setRole(
                Role.ROLE_USER);

        user.setEnabled(true);

        user.setAccountNonLocked(true);

        return user;
    }
}