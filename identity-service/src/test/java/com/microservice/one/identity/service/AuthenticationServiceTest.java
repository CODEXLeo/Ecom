package com.microservice.one.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.microservice.one.identity.constants.SecurityConstants;
import com.microservice.one.identity.dto.request.LoginRequest;
import com.microservice.one.identity.dto.request.RefreshTokenRequest;
import com.microservice.one.identity.dto.request.RegisterRequest;
import com.microservice.one.identity.dto.response.AuthResponse;
import com.microservice.one.identity.dto.response.RegisterResponse;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.enums.Role;
import com.microservice.one.identity.exception.InvalidTokenException;
import com.microservice.one.identity.exception.UserAlreadyExistsException;
import com.microservice.one.identity.exception.UserNotFoundException;
import com.microservice.one.identity.jwt.JwtService;
import com.microservice.one.identity.mapper.UserMapper;
import com.microservice.one.identity.redis.RefreshTokenService;
import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.service.impl.AuthenticationServiceImpl;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    private AuthenticationService authenticationService;

    private UUID userId;

    private User user;

    private UserResponse userResponse;

    private final String email =
            "swata@example.com";

    private final String deviceId =
            "windows-desktop";

    @BeforeEach
    void setUp() {

        authenticationService =
                new AuthenticationServiceImpl(
                        authenticationManager,
                        passwordEncoder,
                        jwtService,
                        refreshTokenService,
                        userRepository,
                        userMapper);

        userId = UUID.randomUUID();

        user = createUser();

        userResponse =
                new UserResponse(
                        userId,
                        "Swatantra",
                        "Naskar",
                        email,
                        Role.ROLE_USER,
                        LocalDateTime.now(),
                        LocalDateTime.now());
    }

    /*
     * ============================================================
     * REGISTER
     * ============================================================
     */

    @Test
    void register_shouldCreateAndPersistNewUser() {

        RegisterRequest request =
                new RegisterRequest(
                        "Swatantra",
                        "Naskar",
                        email,
                        "Password@123");

        when(userRepository.existsByEmail(email))
                .thenReturn(false);

        when(passwordEncoder.encode(
                request.password()))
                .thenReturn("encoded-password");

        when(userMapper.toEntity(
                request,
                "encoded-password"))
                .thenReturn(user);

        when(userRepository.save(user))
                .thenReturn(user);

        RegisterResponse result =
                authenticationService.register(request);

        assertThat(result)
                .isNotNull();

        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(result.firstName())
                .isEqualTo("Swatantra");

        assertThat(result.lastName())
                .isEqualTo("Naskar");

        assertThat(result.email())
                .isEqualTo(email);

        assertThat(result.role())
                .isEqualTo(Role.ROLE_USER);

        assertThat(result.message())
                .isEqualTo(
                        "Registration successful. Please login.");

        verify(userRepository)
                .existsByEmail(email);

        verify(passwordEncoder)
                .encode(request.password());

        verify(userMapper)
                .toEntity(
                        request,
                        "encoded-password");

        verify(userRepository)
                .save(user);

        verifyNoMoreInteractions(
                userRepository,
                passwordEncoder,
                userMapper);
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {

        RegisterRequest request =
                new RegisterRequest(
                        "Swatantra",
                        "Naskar",
                        email,
                        "Password@123");

        when(userRepository.existsByEmail(email))
                .thenReturn(true);

        assertThatThrownBy(
                () -> authenticationService.register(request))
                .isInstanceOf(
                        UserAlreadyExistsException.class)
                .hasMessage(
                        "User already exists with email: "
                                + email);

        verify(userRepository)
                .existsByEmail(email);

        verify(userRepository, never())
                .save(any());

        verify(passwordEncoder, never())
                .encode(any());

        verify(userMapper, never())
                .toEntity(
                        any(RegisterRequest.class),
                        any());

        verifyNoMoreInteractions(
                userRepository);
    }

    /*
     * ============================================================
     * LOGIN
     * ============================================================
     */

    @Test
    void login_shouldAuthenticateGenerateTokensReplaceDeviceSessionAndReturnResponse() {

        LoginRequest request =
                new LoginRequest(
                        email,
                        "Password@123",
                        deviceId);

        String accessToken =
                "access-token";

        String refreshToken =
                "refresh-token";

        String refreshTokenId =
                "refresh-jti";

        long refreshExpiration =
                604800L;

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(jwtService.generateAccessToken(
                userId,
                email,
                Role.ROLE_USER.name()))
                .thenReturn(accessToken);

        when(jwtService.generateRefreshToken(
                userId,
                email,
                deviceId))
                .thenReturn(refreshToken);

        when(jwtService.extractJwtIdFromRefreshToken(
                refreshToken))
                .thenReturn(refreshTokenId);

        when(jwtService.getRefreshExpirationSeconds())
                .thenReturn(refreshExpiration);

        when(jwtService.getAccessExpirationSeconds())
                .thenReturn(900L);

        when(userMapper.toUserResponse(user))
                .thenReturn(userResponse);

        AuthResponse result =
                authenticationService.login(request);

        assertThat(result)
                .isNotNull();

        assertThat(result.accessToken())
                .isEqualTo(accessToken);

        assertThat(result.refreshToken())
                .isEqualTo(refreshToken);

        assertThat(result.tokenType())
                .isEqualTo(
                        SecurityConstants.TOKEN_TYPE);

        assertThat(result.expiresIn())
                .isEqualTo(900L);

        assertThat(result.refreshExpiresIn())
                .isEqualTo(refreshExpiration);

        assertThat(result.user())
                .isSameAs(userResponse);

        verify(authenticationManager)
                .authenticate(
                        any(
                                UsernamePasswordAuthenticationToken.class));

        verify(userRepository)
                .findByEmail(email);

        verify(jwtService)
                .generateAccessToken(
                        userId,
                        email,
                        Role.ROLE_USER.name());

        verify(jwtService)
                .generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        verify(jwtService)
                .extractJwtIdFromRefreshToken(
                        refreshToken);

        verify(refreshTokenService)
                .deleteByUserAndDevice(
                        userId,
                        deviceId);

        ArgumentCaptor<String> idCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> tokenCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<UUID> userIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        ArgumentCaptor<String> emailCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> deviceCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<Long> expirationCaptor =
                ArgumentCaptor.forClass(Long.class);

        verify(refreshTokenService)
                .save(
                        idCaptor.capture(),
                        tokenCaptor.capture(),
                        userIdCaptor.capture(),
                        emailCaptor.capture(),
                        deviceCaptor.capture(),
                        expirationCaptor.capture());

        assertThat(idCaptor.getValue())
                .isEqualTo(refreshTokenId);

        assertThat(tokenCaptor.getValue())
                .isEqualTo(refreshToken);

        assertThat(userIdCaptor.getValue())
                .isEqualTo(userId);

        assertThat(emailCaptor.getValue())
                .isEqualTo(email);

        assertThat(deviceCaptor.getValue())
                .isEqualTo(deviceId);

        assertThat(expirationCaptor.getValue())
                .isEqualTo(refreshExpiration);

        verify(userMapper)
                .toUserResponse(user);

        verifyNoMoreInteractions(
                authenticationManager,
                userRepository,
                jwtService,
                refreshTokenService,
                userMapper);
    }

    @Test
    void login_shouldPropagateAuthenticationFailure() {

        LoginRequest request =
                new LoginRequest(
                        email,
                        "WrongPassword@123",
                        deviceId);

        AuthenticationException exception =
                new AuthenticationException(
                        "Invalid credentials") {
                };

        when(authenticationManager.authenticate(
                any(
                        UsernamePasswordAuthenticationToken.class)))
                .thenThrow(exception);

        assertThatThrownBy(
                () -> authenticationService.login(request))
                .isSameAs(exception);

        verify(authenticationManager)
                .authenticate(
                        any(
                                UsernamePasswordAuthenticationToken.class));

        verify(userRepository, never())
                .findByEmail(any());

        verifyNoMoreInteractions(
                authenticationManager);
    }

    @Test
    void login_shouldThrowWhenAuthenticatedUserCannotBeFound() {

        LoginRequest request =
                new LoginRequest(
                        email,
                        "Password@123",
                        deviceId);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> authenticationService.login(request))
                .isInstanceOf(
                        UserNotFoundException.class)
                .hasMessage(
                        "User not found with email: "
                                + email);

        verify(authenticationManager)
                .authenticate(
                        any(
                                UsernamePasswordAuthenticationToken.class));

        verify(userRepository)
                .findByEmail(email);

        verify(jwtService, never())
                .generateAccessToken(
                        any(),
                        any(),
                        any());

        verify(refreshTokenService, never())
                .save(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyLong());

        verifyNoMoreInteractions(
                authenticationManager,
                userRepository,
                jwtService,
                refreshTokenService);
    }

    /*
     * ============================================================
     * REFRESH TOKEN
     * ============================================================
     */

    @Test
    void refreshToken_shouldRotateRefreshTokenAndReturnNewTokens() {

        String oldRefreshToken =
                "old-refresh-token";

        String oldJwtId =
                "old-jti";

        String newAccessToken =
                "new-access-token";

        String newRefreshToken =
                "new-refresh-token";

        String newJwtId =
                "new-jti";

        long refreshExpiration =
                604800L;

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        oldRefreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                oldRefreshToken))
                .thenReturn(true);

        when(jwtService.extractJwtIdFromRefreshToken(
                oldRefreshToken))
                .thenReturn(oldJwtId);

        when(refreshTokenService.exists(
                oldRefreshToken))
                .thenReturn(true);

        when(jwtService.extractEmailFromRefreshToken(
                oldRefreshToken))
                .thenReturn(email);

        when(jwtService.extractDeviceIdFromRefreshToken(
                oldRefreshToken))
                .thenReturn(deviceId);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(jwtService.isRefreshTokenValid(
                oldRefreshToken,
                userId,
                email))
                .thenReturn(true);

        when(jwtService.generateAccessToken(
                userId,
                email,
                Role.ROLE_USER.name()))
                .thenReturn(newAccessToken);

        when(jwtService.generateRefreshToken(
                userId,
                email,
                deviceId))
                .thenReturn(newRefreshToken);

        when(jwtService.extractJwtIdFromRefreshToken(
                newRefreshToken))
                .thenReturn(newJwtId);

        when(jwtService.getRefreshExpirationSeconds())
                .thenReturn(refreshExpiration);

        when(jwtService.getAccessExpirationSeconds())
                .thenReturn(900L);

        when(userMapper.toUserResponse(user))
                .thenReturn(userResponse);

        AuthResponse result =
                authenticationService.refreshToken(request);

        assertThat(result)
                .isNotNull();

        assertThat(result.accessToken())
                .isEqualTo(newAccessToken);

        assertThat(result.refreshToken())
                .isEqualTo(newRefreshToken);

        assertThat(result.tokenType())
                .isEqualTo(
                        SecurityConstants.TOKEN_TYPE);

        assertThat(result.expiresIn())
                .isEqualTo(900L);

        assertThat(result.refreshExpiresIn())
                .isEqualTo(refreshExpiration);

        assertThat(result.user())
                .isSameAs(userResponse);

        verify(jwtService)
                .canParseRefreshToken(
                        oldRefreshToken);

        verify(jwtService)
                .extractJwtIdFromRefreshToken(
                        oldRefreshToken);

        verify(refreshTokenService)
                .exists(oldRefreshToken);

        verify(jwtService)
                .extractEmailFromRefreshToken(
                        oldRefreshToken);

        verify(jwtService)
                .extractDeviceIdFromRefreshToken(
                        oldRefreshToken);

        verify(userRepository)
                .findByEmail(email);

        verify(jwtService)
                .isRefreshTokenValid(
                        oldRefreshToken,
                        userId,
                        email);

        verify(refreshTokenService)
                .delete(oldJwtId);

        verify(jwtService)
                .generateAccessToken(
                        userId,
                        email,
                        Role.ROLE_USER.name());

        verify(jwtService)
                .generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        verify(jwtService)
                .extractJwtIdFromRefreshToken(
                        newRefreshToken);

        verify(refreshTokenService)
                .save(
                        newJwtId,
                        newRefreshToken,
                        userId,
                        email,
                        deviceId,
                        refreshExpiration);

        verify(userMapper)
                .toUserResponse(user);

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService,
                userRepository,
                userMapper);
    }

    @Test
    void refreshToken_shouldRejectUnparseableRefreshToken() {

        String refreshToken =
                "invalid-refresh-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(false);

        assertThatThrownBy(
                () -> authenticationService.refreshToken(request))
                .isInstanceOf(
                        InvalidTokenException.class)
                .hasMessage(
                        "Invalid refresh token.");

        verify(jwtService)
                .canParseRefreshToken(
                        refreshToken);

        verify(refreshTokenService, never())
                .exists(any());

        verify(userRepository, never())
                .findByEmail(any());

        verifyNoMoreInteractions(
                jwtService);
    }

    @Test
    void refreshToken_shouldRejectReplayedRefreshToken() {

        String refreshToken =
                "already-used-refresh-token";

        String jwtId =
                "already-used-jti";

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractJwtIdFromRefreshToken(
                refreshToken))
                .thenReturn(jwtId);

        when(refreshTokenService.exists(
                refreshToken))
                .thenReturn(false);

        assertThatThrownBy(
                () -> authenticationService.refreshToken(request))
                .isInstanceOf(
                        InvalidTokenException.class)
                .hasMessage(
                        "Refresh token has already been used.");

        verify(jwtService)
                .canParseRefreshToken(
                        refreshToken);

        verify(jwtService)
                .extractJwtIdFromRefreshToken(
                        refreshToken);

        verify(refreshTokenService)
                .exists(refreshToken);

        verify(jwtService, never())
                .extractEmailFromRefreshToken(
                        any());

        verify(userRepository, never())
                .findByEmail(any());

        verify(refreshTokenService, never())
                .delete(any());

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService);
    }

    @Test
    void refreshToken_shouldRejectTokenFromAnotherDevice() {

        String refreshToken =
                "refresh-token";

        String jwtId =
                "refresh-jti";

        String tokenDevice =
                "android-phone";

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractJwtIdFromRefreshToken(
                refreshToken))
                .thenReturn(jwtId);

        when(refreshTokenService.exists(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractEmailFromRefreshToken(
                refreshToken))
                .thenReturn(email);

        when(jwtService.extractDeviceIdFromRefreshToken(
                refreshToken))
                .thenReturn(tokenDevice);

        assertThatThrownBy(
                () -> authenticationService.refreshToken(request))
                .isInstanceOf(
                        InvalidTokenException.class)
                .hasMessage(
                        "Refresh token belongs to another device.");

        verify(jwtService)
                .canParseRefreshToken(
                        refreshToken);

        verify(jwtService)
                .extractJwtIdFromRefreshToken(
                        refreshToken);

        verify(refreshTokenService)
                .exists(refreshToken);

        verify(jwtService)
                .extractEmailFromRefreshToken(
                        refreshToken);

        verify(jwtService)
                .extractDeviceIdFromRefreshToken(
                        refreshToken);

        verify(userRepository, never())
                .findByEmail(any());

        verify(refreshTokenService, never())
                .delete(any());

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService);
    }

    @Test
    void refreshToken_shouldThrowWhenUserDoesNotExist() {

        String refreshToken =
                "refresh-token";

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractJwtIdFromRefreshToken(
                refreshToken))
                .thenReturn("refresh-jti");

        when(refreshTokenService.exists(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractEmailFromRefreshToken(
                refreshToken))
                .thenReturn(email);

        when(jwtService.extractDeviceIdFromRefreshToken(
                refreshToken))
                .thenReturn(deviceId);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        assertThatThrownBy(
                () -> authenticationService.refreshToken(request))
                .isInstanceOf(
                        UserNotFoundException.class)
                .hasMessage(
                        "User not found with email: "
                                + email);

        verify(userRepository)
                .findByEmail(email);

        verify(jwtService, never())
                .isRefreshTokenValid(
                        any(),
                        any(),
                        any());

        verify(refreshTokenService, never())
                .delete(any());

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService,
                userRepository);
    }

    @Test
    void refreshToken_shouldRejectInvalidTokenForUser() {

        String refreshToken =
                "invalid-user-refresh-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractJwtIdFromRefreshToken(
                refreshToken))
                .thenReturn("refresh-jti");

        when(refreshTokenService.exists(
                refreshToken))
                .thenReturn(true);

        when(jwtService.extractEmailFromRefreshToken(
                refreshToken))
                .thenReturn(email);

        when(jwtService.extractDeviceIdFromRefreshToken(
                refreshToken))
                .thenReturn(deviceId);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(jwtService.isRefreshTokenValid(
                refreshToken,
                userId,
                email))
                .thenReturn(false);

        assertThatThrownBy(
                () -> authenticationService.refreshToken(request))
                .isInstanceOf(
                        InvalidTokenException.class)
                .hasMessage(
                        "Refresh token is invalid.");

        verify(jwtService)
                .isRefreshTokenValid(
                        refreshToken,
                        userId,
                        email);

        verify(refreshTokenService, never())
                .delete(any());

        verify(jwtService, never())
                .generateAccessToken(
                        any(),
                        any(),
                        any());

        verify(refreshTokenService, never())
                .save(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyLong());

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService,
                userRepository);
    }

    /*
     * ============================================================
     * LOGOUT
     * ============================================================
     */

    @Test
    void logout_shouldDeleteRefreshTokenByJwtId() {

        String refreshToken = "refresh-token";
        String jwtId = "refresh-jti";

        when(jwtService.canParseRefreshToken(refreshToken)).thenReturn(true);

        when(jwtService.extractDeviceIdFromRefreshToken(refreshToken)).thenReturn(deviceId);

        when(jwtService.extractJwtIdFromRefreshToken(refreshToken)).thenReturn(jwtId);

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken, deviceId);

        authenticationService.logout(request);

        verify(jwtService).canParseRefreshToken(refreshToken);

        verify(jwtService).extractDeviceIdFromRefreshToken(refreshToken);

        verify(jwtService).extractJwtIdFromRefreshToken(refreshToken);

        verify(refreshTokenService).delete(jwtId);

        verifyNoMoreInteractions(jwtService, refreshTokenService);
    }

    @Test
    void logout_shouldIgnoreInvalidRefreshToken() {

        String refreshToken =
                "invalid-refresh-token";

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        refreshToken,
                        deviceId);

        when(jwtService.canParseRefreshToken(
                refreshToken))
                .thenReturn(false);

        authenticationService.logout(request);

        verify(jwtService)
                .canParseRefreshToken(
                        refreshToken);

        verify(jwtService, never())
                .extractJwtIdFromRefreshToken(
                        any());

        verify(refreshTokenService, never())
                .delete(any());

        verifyNoMoreInteractions(
                jwtService,
                refreshTokenService);
    }

    /*
     * ============================================================
     * TEST DATA
     * ============================================================
     */

    private User createUser() {

        User user =
                new User();

        user.setUserId(userId);

        user.setFirstName("Swatantra");

        user.setLastName("Naskar");

        user.setEmail(email);

        user.setPasswordHash("encoded-password");

        user.setRole(Role.ROLE_USER);

        return user;
    }
}