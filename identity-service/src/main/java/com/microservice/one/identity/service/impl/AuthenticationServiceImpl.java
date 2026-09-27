package com.microservice.one.identity.service.impl;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.one.identity.constants.SecurityConstants;
import com.microservice.one.identity.dto.request.LoginRequest;
import com.microservice.one.identity.dto.request.RefreshTokenRequest;
import com.microservice.one.identity.dto.request.RegisterRequest;
import com.microservice.one.identity.dto.response.AuthResponse;
import com.microservice.one.identity.dto.response.RegisterResponse;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.exception.InvalidTokenException;
import com.microservice.one.identity.exception.UserAlreadyExistsException;
import com.microservice.one.identity.exception.UserNotFoundException;
import com.microservice.one.identity.jwt.JwtService;
import com.microservice.one.identity.mapper.UserMapper;
import com.microservice.one.identity.redis.RefreshTokenService;
import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.service.AuthenticationService;

@Service
public class AuthenticationServiceImpl
        implements AuthenticationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AuthenticationServiceImpl.class);

    private final AuthenticationManager authenticationManager;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    public AuthenticationServiceImpl(

            AuthenticationManager authenticationManager,

            PasswordEncoder passwordEncoder,

            JwtService jwtService,

            RefreshTokenService refreshTokenService,

            UserRepository userRepository,

            UserMapper userMapper) {

        this.authenticationManager =
                authenticationManager;

        this.passwordEncoder =
                passwordEncoder;

        this.jwtService =
                jwtService;

        this.refreshTokenService =
                refreshTokenService;

        this.userRepository =
                userRepository;

        this.userMapper =
                userMapper;
    }

    /*
     * ============================================================
     * REGISTER
     * ============================================================
     */

    @Override
    @Transactional
    public RegisterResponse register(
            RegisterRequest registerRequest) {

        LOGGER.info(
                "Registration request received for {}",
                registerRequest.email());

        if (userRepository.existsByEmail(
                registerRequest.email())) {

            throw new UserAlreadyExistsException(
                    "User already exists with email: "
                            + registerRequest.email());
        }

        String encodedPassword =
                passwordEncoder.encode(
                        registerRequest.password());

        User user =
                userMapper.toEntity(
                        registerRequest,
                        encodedPassword);

        User savedUser =
                userRepository.save(user);

        LOGGER.info(
                "User registered successfully. userId={} email={}",
                savedUser.getUserId(),
                savedUser.getEmail());

        return new RegisterResponse(

                savedUser.getUserId(),

                savedUser.getFirstName(),

                savedUser.getLastName(),

                savedUser.getEmail(),

                savedUser.getRole(),

                savedUser.getCreatedAt(),

                "Registration successful. Please login.");
    }

    /*
     * ============================================================
     * LOGIN
     * ============================================================
     */

    @Override
    public AuthResponse login(
            LoginRequest loginRequest) {

        LOGGER.info(
                "Login request received for {}",
                loginRequest.email());

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.email(),
                            loginRequest.password()));

        } catch (AuthenticationException authenticationException) {

            LOGGER.warn(
                    "Authentication failed for {}",
                    loginRequest.email());

            throw authenticationException;
        }

        User user =
                userRepository
                        .findByEmail(
                                loginRequest.email())
                        .orElseThrow(
                                () ->
                                        new UserNotFoundException(
                                                "User not found with email: "
                                                        + loginRequest.email()));

        String accessToken =
                jwtService.generateAccessToken(
                        user.getUserId(),
                        user.getEmail(),
                        user.getRole().name());

        String refreshToken =
                jwtService.generateRefreshToken(
                        user.getUserId(),
                        user.getEmail(),
                        loginRequest.deviceId());

        String refreshTokenId =
                jwtService.extractJwtIdFromRefreshToken(
                        refreshToken);

        /*
         * Only one active refresh token is maintained
         * for a particular user/device combination.
         */
        refreshTokenService.deleteByUserAndDevice(
                user.getUserId(),
                loginRequest.deviceId());

        refreshTokenService.save(

                refreshTokenId,

                refreshToken,

                user.getUserId(),

                user.getEmail(),

                loginRequest.deviceId(),

                jwtService.getRefreshExpirationSeconds());

        LOGGER.info(
                "User {} logged in successfully on device {}",
                user.getEmail(),
                loginRequest.deviceId());

        return buildAuthResponse(
                user,
                accessToken,
                refreshToken);
    }

    /*
     * ============================================================
     * LOGOUT
     * ============================================================
     */

    @Override
    public void logout(
            RefreshTokenRequest refreshTokenRequest) {

        LOGGER.info(
                "Logout requested.");

        String refreshToken =
                refreshTokenRequest.refreshToken();

        /*
         * Keep logout idempotent for malformed/invalid
         * refresh tokens.
         */
        if (!jwtService.canParseRefreshToken(refreshToken)) {
            LOGGER.warn("Logout ignored because refresh token is invalid.");
            return;
        }

        /*
         * Extract the device associated with the refresh token.
         */
        String tokenDeviceId = jwtService.extractDeviceIdFromRefreshToken(refreshToken);

        /*
         * The device supplied by the client must match
         * the device encoded inside the refresh token.
         *
         * This prevents a refresh token belonging to
         * Device A from being revoked using Device B.
         */
        if (!Objects.equals(tokenDeviceId, refreshTokenRequest.deviceId())) {
            LOGGER.warn(
                    "Logout rejected because refresh token belongs "
                            + "to device {} but request came from device {}.",
                    tokenDeviceId,
                    refreshTokenRequest.deviceId());

            throw new InvalidTokenException(
                    "Refresh token belongs to another device.");
        }

        String refreshTokenId = jwtService.extractJwtIdFromRefreshToken(refreshToken);

        refreshTokenService.delete(refreshTokenId);

        LOGGER.info(
                "Refresh token {} invalidated for device {}.",
                refreshTokenId,
                tokenDeviceId);
    }

    /*
     * ============================================================
     * REFRESH TOKEN
     * ============================================================
     */

    @Override
    public AuthResponse refreshToken(
            RefreshTokenRequest refreshTokenRequest) {

        String oldRefreshToken =
                refreshTokenRequest.refreshToken();

        /*
         * Validate that the JWT can be parsed as a
         * refresh token.
         */
        if (!jwtService.canParseRefreshToken(
                oldRefreshToken)) {

            throw new InvalidTokenException(
                    "Invalid refresh token.");
        }

        String jwtId =
                jwtService.extractJwtIdFromRefreshToken(
                        oldRefreshToken);

        /*
         * The old refresh token must still exist in Redis.
         *
         * If it does not exist, it has either already been
         * rotated or explicitly revoked by logout.
         */
        if (!refreshTokenService.exists(
                oldRefreshToken)) {

            LOGGER.warn(
                    "Refresh token replay attack detected. jti={}",
                    jwtId);

            throw new InvalidTokenException(
                    "Refresh token has already been used.");
        }

        String email =
                jwtService.extractEmailFromRefreshToken(
                        oldRefreshToken);

        String deviceId =
                jwtService.extractDeviceIdFromRefreshToken(
                        oldRefreshToken);

        /*
         * The device supplied with the request must match
         * the device encoded in the refresh token.
         */
        if (!Objects.equals(deviceId, refreshTokenRequest.deviceId())) {

            throw new InvalidTokenException(
                    "Refresh token belongs to another device.");
        }

        LOGGER.info(
                "Refreshing token for {}",
                email);

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new UserNotFoundException(
                                                "User not found with email: "
                                                        + email));

        /*
         * Validate the refresh token against the user.
         */
        if (!jwtService.isRefreshTokenValid(
                oldRefreshToken,
                user.getUserId(),
                user.getEmail())) {

            throw new InvalidTokenException(
                    "Refresh token is invalid.");
        }

        /*
         * Rotate the refresh token.
         */
        refreshTokenService.delete(
                jwtId);

        String newAccessToken =
                jwtService.generateAccessToken(
                        user.getUserId(),
                        user.getEmail(),
                        user.getRole().name());

        String newRefreshToken =
                jwtService.generateRefreshToken(
                        user.getUserId(),
                        user.getEmail(),
                        deviceId);

        String newJwtId =
                jwtService.extractJwtIdFromRefreshToken(
                        newRefreshToken);

        refreshTokenService.save(

                newJwtId,

                newRefreshToken,

                user.getUserId(),

                user.getEmail(),

                deviceId,

                jwtService.getRefreshExpirationSeconds());

        LOGGER.info(
                "Refresh token rotated successfully for {} device={}",
                user.getEmail(),
                deviceId);

        return buildAuthResponse(
                user,
                newAccessToken,
                newRefreshToken);
    }

    /*
     * ============================================================
     * AUTH RESPONSE BUILDER
     * ============================================================
     */

    private AuthResponse buildAuthResponse(

            User user,

            String accessToken,

            String refreshToken) {

        UserResponse userResponse =
                userMapper.toUserResponse(user);

        return new AuthResponse(

                accessToken,

                refreshToken,

                SecurityConstants.TOKEN_TYPE,

                jwtService.getAccessExpirationSeconds(),

                jwtService.getRefreshExpirationSeconds(),

                userResponse);
    }
}