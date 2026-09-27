package com.microservice.one.identity.redis;

import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Store a refresh token session in Redis.
     */
    void save(
            String id,
            String token,
            UUID userId,
            String email,
            String deviceId,
            long expirationSeconds);

    /**
     * Find a refresh token by JWT string.
     */
    RefreshToken findByToken(
            String token);

    /**
     * Check whether a refresh token exists.
     */
    boolean exists(
            String token);

    /**
     * Delete a refresh token session by JWT ID.
     */
    void delete(
            String id);

    /**
     * Delete all refresh tokens for one device.
     */
    void deleteByUserAndDevice(
            UUID userId,
            String deviceId);

    /**
     * Delete every refresh token for the user.
     */
    void deleteAllByUser(
            UUID userId);

}