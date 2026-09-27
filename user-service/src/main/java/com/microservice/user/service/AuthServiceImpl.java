package com.microservice.user.service;

import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.user.dto.request.LoginRequest;
import com.microservice.user.dto.request.RegisterRequest;
import com.microservice.user.dto.response.LoginResponse;
import com.microservice.user.dto.response.RegisterResponse;
import com.microservice.user.entity.User;
import com.microservice.user.exception.EmailAlreadyExistsException;
import com.microservice.user.exception.InvalidCredentialsException;
import com.microservice.user.exception.RefreshTokenReuseException;
import com.microservice.user.mapper.UserMapper;
import com.microservice.user.repository.UserRepository;
import com.microservice.user.security.auth.AuthenticationProperties;
import com.microservice.user.security.cookie.AuthCookieProperties;
import com.microservice.user.security.cookie.CookieService;
import com.microservice.user.security.jwt.JwtService;
import com.microservice.user.service.RefreshSessionService.CreatedRefreshSession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshSessionService refreshSessionService;
    private final AuthCookieProperties cookieProperties;
    private final CookieService cookieService;
    private final AuthenticationProperties authenticationProperties;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshSessionService refreshSessionService,
            AuthCookieProperties cookieProperties,
            CookieService cookieService,
            AuthenticationProperties authenticationProperties) {

        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshSessionService = refreshSessionService;
        this.cookieProperties = cookieProperties;
        this.cookieService = cookieService;
        this.authenticationProperties = authenticationProperties;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        String normalizedEmail = normalizeEmail(registerRequest.email());
        log.info("Registration attempt for email={}", normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected because email already exists: email={}", normalizedEmail);
            throw new EmailAlreadyExistsException();
        }

        String encodedPassword = passwordEncoder.encode(registerRequest.password());
        User user = userMapper.toEntity(registerRequest, encodedPassword, normalizedEmail);
        User savedUser = userRepository.save(user);
        log.info("User registration successful: userId={}, email={}", savedUser.getUserId(), savedUser.getEmail());
        return userMapper.toRegisterResponse(savedUser);
    }
    
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(
            LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String normalizedEmail = normalizeEmail(loginRequest.email());

        log.info(
                "Login attempt for email={}",
                normalizedEmail);

        /*
         * Lock the user's database row for the entire login
         * transaction.
         *
         * This prevents concurrent failed-login requests from
         * racing when modifying failedLoginAttempts.
         */
        User user =
                userRepository
                        .findByEmailForUpdate(
                                normalizedEmail)
                        .orElseThrow(
                                InvalidCredentialsException::new);

        Instant now = Instant.now();

        /*
         * If a temporary lock has expired, automatically clear it.
         */
        user.unlockIfExpired(now);

        /*
         * Disabled accounts can never authenticate.
         *
         * We deliberately return the same generic authentication
         * error so callers cannot distinguish account states.
         */
        if (!user.isEnabled()) {

            log.warn(
                    "Login rejected because account is disabled: userId={}",
                    user.getUserId());

            throw new InvalidCredentialsException();
        }

        /*
         * Account is still temporarily locked.
         */
        if (user.isAccountLocked()) {

            log.warn(
                    "Login rejected because account is temporarily locked: userId={}, failedAttempts={}",
                    user.getUserId(),
                    user.getFailedLoginAttempts());

            throw new InvalidCredentialsException();
        }

        /*
         * Verify the password.
         */
        boolean passwordMatches =
                passwordEncoder.matches(
                        loginRequest.password(),
                        user.getPasswordHash());

        if (!passwordMatches) {

            user.recordFailedLoginAttempt(
                    authenticationProperties
                            .getMaximumFailedLoginAttempts(),
                    now,
                    authenticationProperties
                            .getAccountLockDuration());

            if (user.isAccountLocked()) {

                log.warn(
                        "Account temporarily locked after too many failed login attempts: userId={}, failedAttempts={}",
                        user.getUserId(),
                        user.getFailedLoginAttempts());

            } else {

                log.warn(
                        "Login rejected due to invalid credentials: userId={}, failedAttempts={}",
                        user.getUserId(),
                        user.getFailedLoginAttempts());
            }

            /*
             * User is a managed JPA entity.
             *
             * The updated failed-login state will be persisted
             * automatically by Hibernate when this transaction commits.
             */
            throw new InvalidCredentialsException();
        }

        /*
         * Successful authentication clears the entire previous
         * failed-login state.
         */
        user.resetFailedLoginAttempts();

        /*
         * Generate short-lived access JWT.
         */
        String accessToken =
                jwtService.generateAccessToken(user);

        /*
         * Create a completely new refresh-token family.
         */
        CreatedRefreshSession refreshSession =
                refreshSessionService.createSession(
                        user,
                        cookieProperties
                                .getRefreshTokenExpiration(),
                        getUserAgent(request),
                        getClientIpAddress(request));

        /*
         * Store access token in HttpOnly cookie.
         */
        cookieService.addAccessTokenCookie(
                response,
                accessToken,
                jwtService.getAccessTokenExpiration());

        /*
         * Store refresh token in HttpOnly cookie.
         */
        cookieService.addRefreshTokenCookie(
                response,
                refreshSession.rawRefreshToken());

        log.info(
                "User login successful: userId={}, sessionId={}",
                user.getUserId(),
                refreshSession.session().getSessionId());

        return userMapper.toLoginResponse(user);
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public void refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String rawRefreshToken =
                extractCookie(
                        request,
                        cookieProperties
                                .getRefreshTokenName());

        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {

            throw new com.microservice.user.exception
                    .InvalidRefreshTokenException();
        }

        CreatedRefreshSession rotatedSession =
                refreshSessionService.rotateRefreshToken(
                        rawRefreshToken,
                        cookieProperties
                                .getRefreshTokenExpiration(),
                        getUserAgent(request),
                        getClientIpAddress(request));

        User user = rotatedSession.session().getUser();

        /*
         * Generate a fresh short-lived access JWT.
         */
        String accessToken =
                jwtService.generateAccessToken(user);

        /*
         * Replace both cookies.
         */
        cookieService.addAccessTokenCookie(
                response,
                accessToken,
                jwtService.getAccessTokenExpiration());

        cookieService.addRefreshTokenCookie(
                response,
                rotatedSession.rawRefreshToken());

        log.info(
                "Refresh successful: userId={}, newSessionId={}",
                user.getUserId(),
                rotatedSession.session().getSessionId());
    }

    @Override
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {

        String rawRefreshToken = extractCookie(request, cookieProperties.getRefreshTokenName());

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshSessionService.revokeByRawToken(rawRefreshToken);
        }

        /*
         * Even if the refresh token is already invalid,
         * always clear the browser cookies.
         */
        cookieService.clearAuthenticationCookies(response);
        log.info("User logout completed");
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {

            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String getUserAgent(
            HttpServletRequest request) {

        String userAgent = request.getHeader("User-Agent");

        if (userAgent == null) {
            return null;
        }

        /*
         * DB column is limited to 1000 characters.
         */
        return userAgent.length() > 1000 ? userAgent.substring(0, 1000) : userAgent;
    }

    private String getClientIpAddress(HttpServletRequest request) {

        /*
         * Do NOT blindly trust X-Forwarded-For yet.
         *
         * When a trusted reverse proxy/Gateway is introduced,
         * configure forwarded-header handling there.
         */
        return request.getRemoteAddr();
    }
}