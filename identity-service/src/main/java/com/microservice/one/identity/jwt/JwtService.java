package com.microservice.one.identity.jwt;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(JwtService.class);

    private final SecretKey accessSecretKey;

    private final SecretKey refreshSecretKey;

    private final JwtProperties jwtProperties;

    public JwtService(
            JwtProperties jwtProperties) {

        this.jwtProperties = jwtProperties;

        this.accessSecretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(
                        jwtProperties.getAccessSecret()));

        this.refreshSecretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(
                        jwtProperties.getRefreshSecret()));

        LOGGER.info(
                "JWT Service initialized. Access={} ms Refresh={} ms",
                jwtProperties.getAccessExpiration(),
                jwtProperties.getRefreshExpiration());

    }

    /*
     * ============================================================
     * ACCESS TOKEN
     * ============================================================
     */

    public String generateAccessToken(

            UUID userId,

            String email,

            String role) {

        LOGGER.debug(
                "Generating ACCESS token for {}",
                email);

        return generateToken(

                userId,

                email,

                role,

                JwtTokenType.ACCESS,

                jwtProperties.getAccessExpiration(),

                accessSecretKey, null);

    }

    /*
     * ============================================================
     * REFRESH TOKEN
     * ============================================================
     */

    public String generateRefreshToken(
            UUID userId,
            String email,
            String deviceId) {

        LOGGER.debug(
                "Generating REFRESH token for {}",
                email);

        return generateToken(
                userId,
                email,
                null,
                JwtTokenType.REFRESH,
                jwtProperties.getRefreshExpiration(),
                refreshSecretKey,
                deviceId);

    }

    /*
     * ============================================================
     * GENERIC TOKEN BUILDER
     * ============================================================
     */

    private String generateToken(
            UUID userId,
            String email,
            String role,
            JwtTokenType tokenType,
            long expiration,
            Key signingKey,
            String deviceId) {

    	Date issuedAt = new Date();
    	Date expiresAt = new Date(issuedAt.getTime() + expiration);

    	String jwtId = UUID.randomUUID().toString();

    	Map<String, Object> claims;

    	if (tokenType == JwtTokenType.ACCESS) {

    	    claims = Map.of(
    	            "userId", userId.toString(),
    	            "role", role,
    	            "type", tokenType.name());

    	} else {

    	    claims = Map.of(
    	            "userId", userId.toString(),
    	            "deviceId", deviceId,
    	            "type", tokenType.name());

    	}

    	return Jwts.builder()
    	        .id(jwtId)
    	        .subject(email)
    	        .claims(claims)
    	        .issuedAt(issuedAt)
    	        .expiration(expiresAt)
    	        .signWith(signingKey)
    	        .compact();

    }
    
    /*
     * ============================================================
     * CLAIM EXTRACTION
     * ============================================================
     */

    public String extractEmail(
            String token) {

        return extractClaim(
                token,
                io.jsonwebtoken.Claims::getSubject,
                accessSecretKey);

    }

    public String extractEmailFromRefreshToken(
            String token) {

        return extractClaim(
                token,
                io.jsonwebtoken.Claims::getSubject,
                refreshSecretKey);

    }

    public UUID extractUserId(
            String token) {

        String userId = extractClaim(
                token,
                claims -> claims.get("userId", String.class),
                accessSecretKey);

        return UUID.fromString(userId);

    }

    public UUID extractUserIdFromRefreshToken(
            String token) {

        String userId = extractClaim(
                token,
                claims -> claims.get("userId", String.class),
                refreshSecretKey);

        return UUID.fromString(userId);

    }

    public String extractRole(
            String token) {

        return extractClaim(
                token,
                claims -> claims.get("role", String.class),
                accessSecretKey);

    }

    public JwtTokenType extractTokenType(
            String token) {

        String tokenType = extractClaim(
                token,
                claims -> claims.get("type", String.class),
                accessSecretKey);

        return JwtTokenType.valueOf(tokenType);

    }

    public JwtTokenType extractRefreshTokenType(
            String token) {

        String tokenType = extractClaim(
                token,
                claims -> claims.get("type", String.class),
                refreshSecretKey);

        return JwtTokenType.valueOf(tokenType);

    }

    /*
     * ============================================================
     * EXPIRATION
     * ============================================================
     */

    public Date extractExpiration(
            String token) {

        return extractClaim(
                token,
                io.jsonwebtoken.Claims::getExpiration,
                accessSecretKey);

    }

    public Date extractRefreshExpiration(
            String token) {

        return extractClaim(
                token,
                io.jsonwebtoken.Claims::getExpiration,
                refreshSecretKey);

    }

    public boolean isAccessTokenExpired(
            String token) {

        return extractExpiration(token)
                .before(new Date());

    }

    public boolean isRefreshTokenExpired(
            String token) {

        return extractRefreshExpiration(token)
                .before(new Date());

    }

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    public boolean isAccessTokenValid(
            String token,
            UUID userId,
            String email) {

        return extractUserId(token).equals(userId)
                && extractEmail(token).equals(email)
                && extractTokenType(token) == JwtTokenType.ACCESS
                && !isAccessTokenExpired(token);

    }
    
    /*
     * ============================================================
     * VALIDATION USING USERDETAILS
     * ============================================================
     */

    public boolean isAccessTokenValid(

            String token,

            UserDetails userDetails) {

        return extractEmail(token)
                .equals(userDetails.getUsername())

                && extractTokenType(token)
                        == JwtTokenType.ACCESS

                && !isAccessTokenExpired(token);

    }

    public boolean isRefreshTokenValid(
            String token,
            UUID userId,
            String email) {

        return extractUserIdFromRefreshToken(token).equals(userId)
                && extractEmailFromRefreshToken(token).equals(email)
                && extractRefreshTokenType(token) == JwtTokenType.REFRESH
                && !isRefreshTokenExpired(token);

    }

    /*
     * ============================================================
     * GENERIC CLAIM HELPERS
     * ============================================================
     */

    public <T> T extractClaim(
            String token,
            java.util.function.Function<io.jsonwebtoken.Claims, T> claimsResolver,
            Key signingKey) {

        io.jsonwebtoken.Claims claims =
                extractAllClaims(
                        token,
                        signingKey);

        return claimsResolver.apply(claims);

    }

    public io.jsonwebtoken.Claims extractAllClaims(
            String token,
            Key signingKey) {

        return Jwts.parser()

                .verifyWith((SecretKey) signingKey)

                .build()

                .parseSignedClaims(token)

                .getPayload();

    }

    /*
     * ============================================================
     * TOKEN PARSING
     * ============================================================
     */

    public boolean canParseAccessToken(
            String token) {

        try {

            extractAllClaims(
                    token,
                    accessSecretKey);

            return true;

        } catch (Exception exception) {

            LOGGER.debug(
                    "Unable to parse access token: {}",
                    exception.getMessage());

            return false;

        }

    }

    public boolean canParseRefreshToken(
            String token) {

        try {

            extractAllClaims(
                    token,
                    refreshSecretKey);

            return true;

        } catch (Exception exception) {

            LOGGER.debug(
                    "Unable to parse refresh token: {}",
                    exception.getMessage());

            return false;

        }

    }
    
    /*
     * ============================================================
     * TOKEN LIFETIMES
     * ============================================================
     */

    public long getAccessExpirationSeconds() {

        return jwtProperties.getAccessExpirationSeconds();

    }

    public long getRefreshExpirationSeconds() {

        return jwtProperties.getRefreshExpirationSeconds();

    }
    
    public String extractJwtIdFromRefreshToken(
            String token) {

        return extractClaim(
                token,
                io.jsonwebtoken.Claims::getId,
                refreshSecretKey);

    }
    
    public String extractDeviceIdFromRefreshToken(
            String token) {

        return extractClaim(
                token,
                claims -> claims.get("deviceId", String.class),
                refreshSecretKey);

    }

}