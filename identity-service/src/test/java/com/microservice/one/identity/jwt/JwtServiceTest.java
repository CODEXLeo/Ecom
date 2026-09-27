package com.microservice.one.identity.jwt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    private static final String ACCESS_SECRET =
            "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdBY2Nlc3NTZWNyZXRLZXlGb3JUZXN0aW5nMTIzNDU2Nzg5";

    private static final String REFRESH_SECRET =
            "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdSZWZyZXNoU2VjcmV0S2V5Rm9yVGVzdGluZzEyMzQ1Njc4OQ==";

    private static final long ACCESS_EXPIRATION = 900_000L;

    private static final long REFRESH_EXPIRATION = 604_800_000L;

    private JwtService jwtService;

    private JwtProperties jwtProperties;

    private UUID userId;

    private String email;

    private String role;

    private String deviceId;

    @BeforeEach
    void setUp() {

        jwtProperties = new JwtProperties();

        jwtProperties.setAccessSecret(ACCESS_SECRET);
        jwtProperties.setRefreshSecret(REFRESH_SECRET);
        jwtProperties.setAccessExpiration(ACCESS_EXPIRATION);
        jwtProperties.setRefreshExpiration(REFRESH_EXPIRATION);

        jwtService = new JwtService(jwtProperties);

        userId = UUID.randomUUID();
        email = "swata@example.com";
        role = "ROLE_USER";
        deviceId = "android-phone";
    }

    @Test
    void generateAccessToken_shouldGenerateValidAccessToken() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertNotNull(token);
        assertFalse(token.isBlank());

        assertTrue(
                jwtService.canParseAccessToken(token));
    }

    @Test
    void generateRefreshToken_shouldGenerateValidRefreshToken() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertNotNull(token);
        assertFalse(token.isBlank());

        assertTrue(
                jwtService.canParseRefreshToken(token));
    }

    @Test
    void accessToken_shouldContainCorrectClaims() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertEquals(
                userId,
                jwtService.extractUserId(token));

        assertEquals(
                email,
                jwtService.extractEmail(token));

        assertEquals(
                role,
                jwtService.extractRole(token));

        assertEquals(
                JwtTokenType.ACCESS,
                jwtService.extractTokenType(token));

        assertNotNull(
                jwtService.extractExpiration(token));
    }

    @Test
    void refreshToken_shouldContainCorrectClaims() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertEquals(
                userId,
                jwtService.extractUserIdFromRefreshToken(token));

        assertEquals(
                email,
                jwtService.extractEmailFromRefreshToken(token));

        assertEquals(
                deviceId,
                jwtService.extractDeviceIdFromRefreshToken(token));

        assertEquals(
                JwtTokenType.REFRESH,
                jwtService.extractRefreshTokenType(token));

        assertNotNull(
                jwtService.extractRefreshExpiration(token));
    }

    @Test
    void accessToken_shouldHaveCorrectJwtId() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        String jwtId =
                jwtService.extractClaim(
                        token,
                        io.jsonwebtoken.Claims::getId,
                        getAccessSigningKey());

        assertNotNull(jwtId);
        assertFalse(jwtId.isBlank());

        assertDoesNotThrow(
                () -> UUID.fromString(jwtId));
    }

    @Test
    void refreshToken_shouldHaveCorrectJwtId() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        String jwtId =
                jwtService.extractJwtIdFromRefreshToken(token);

        assertNotNull(jwtId);
        assertFalse(jwtId.isBlank());

        assertDoesNotThrow(
                () -> UUID.fromString(jwtId));
    }

    @Test
    void accessToken_shouldBeValidForCorrectUser() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertTrue(
                jwtService.isAccessTokenValid(
                        token,
                        userId,
                        email));
    }

    @Test
    void accessToken_shouldBeInvalidForDifferentUserId() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        UUID differentUserId =
                UUID.randomUUID();

        assertFalse(
                jwtService.isAccessTokenValid(
                        token,
                        differentUserId,
                        email));
    }

    @Test
    void accessToken_shouldBeInvalidForDifferentEmail() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertFalse(
                jwtService.isAccessTokenValid(
                        token,
                        userId,
                        "different@example.com"));
    }

    @Test
    void accessToken_shouldBeValidForMatchingUserDetails() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(email)
                        .password("ignored")
                        .roles("USER")
                        .build();

        assertTrue(
                jwtService.isAccessTokenValid(
                        token,
                        userDetails));
    }

    @Test
    void accessToken_shouldBeInvalidForDifferentUserDetails() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername("different@example.com")
                        .password("ignored")
                        .roles("USER")
                        .build();

        assertFalse(
                jwtService.isAccessTokenValid(
                        token,
                        userDetails));
    }

    @Test
    void refreshToken_shouldBeValidForCorrectUser() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertTrue(
                jwtService.isRefreshTokenValid(
                        token,
                        userId,
                        email));
    }

    @Test
    void refreshToken_shouldBeInvalidForDifferentUserId() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        UUID differentUserId =
                UUID.randomUUID();

        assertFalse(
                jwtService.isRefreshTokenValid(
                        token,
                        differentUserId,
                        email));
    }

    @Test
    void refreshToken_shouldBeInvalidForDifferentEmail() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertFalse(
                jwtService.isRefreshTokenValid(
                        token,
                        userId,
                        "different@example.com"));
    }

    @Test
    void accessToken_shouldNotBeAcceptedAsRefreshToken() {

        String accessToken =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertFalse(
                jwtService.canParseRefreshToken(
                        accessToken));
    }

    @Test
    void refreshToken_shouldNotBeAcceptedAsAccessToken() {

        String refreshToken =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertFalse(
                jwtService.canParseAccessToken(
                        refreshToken));
    }

    @Test
    void accessToken_shouldHaveExpectedLifetime() {

        long before = System.currentTimeMillis();

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        Date expiration =
                jwtService.extractExpiration(token);

        long after = System.currentTimeMillis();

        long tolerance = 1000;

        long expectedMinimum =
                before + ACCESS_EXPIRATION - tolerance;

        long expectedMaximum =
                after + ACCESS_EXPIRATION + tolerance;

        assertTrue(
                expiration.getTime() >= expectedMinimum);

        assertTrue(
                expiration.getTime() <= expectedMaximum);
    }

    @Test
    void refreshToken_shouldHaveExpectedLifetime() {

        long before = System.currentTimeMillis();

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        Date expiration =
                jwtService.extractRefreshExpiration(token);

        long after = System.currentTimeMillis();

        long tolerance = 1000;

        long expectedMinimum =
                before + REFRESH_EXPIRATION - tolerance;

        long expectedMaximum =
                after + REFRESH_EXPIRATION + tolerance;

        assertTrue(
                expiration.getTime() >= expectedMinimum);

        assertTrue(
                expiration.getTime() <= expectedMaximum);
    }

    @Test
    void accessToken_shouldNotBeExpiredImmediately() {

        String token =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertFalse(
                jwtService.isAccessTokenExpired(
                        token));
    }

    @Test
    void refreshToken_shouldNotBeExpiredImmediately() {

        String token =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertFalse(
                jwtService.isRefreshTokenExpired(
                        token));
    }

    @Test
    void canParseAccessToken_shouldReturnFalseForInvalidToken() {

        assertFalse(
                jwtService.canParseAccessToken(
                        "invalid.jwt.token"));
    }

    @Test
    void canParseRefreshToken_shouldReturnFalseForInvalidToken() {

        assertFalse(
                jwtService.canParseRefreshToken(
                        "invalid.jwt.token"));
    }

    @Test
    void canParseAccessToken_shouldReturnFalseForRefreshTokenSignedWithDifferentKey() {

        String refreshToken =
                jwtService.generateRefreshToken(
                        userId,
                        email,
                        deviceId);

        assertFalse(
                jwtService.canParseAccessToken(
                        refreshToken));
    }

    @Test
    void canParseRefreshToken_shouldReturnFalseForAccessTokenSignedWithDifferentKey() {

        String accessToken =
                jwtService.generateAccessToken(
                        userId,
                        email,
                        role);

        assertFalse(
                jwtService.canParseRefreshToken(
                        accessToken));
    }

    @Test
    void accessExpirationSeconds_shouldBeCorrect() {

        assertEquals(
                900,
                jwtService.getAccessExpirationSeconds());
    }

    @Test
    void refreshExpirationSeconds_shouldBeCorrect() {

        assertEquals(
                604800,
                jwtService.getRefreshExpirationSeconds());
    }

    private javax.crypto.SecretKey getAccessSigningKey() {

        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                        ACCESS_SECRET));
    }
}