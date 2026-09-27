package com.microservice.one.identity.redis;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

/**
 * Refresh token session stored in Redis.
 */
@RedisHash("refresh_tokens")
public class RefreshToken implements Serializable {

    private static final long serialVersionUID = 1L;

    /*
     * Redis primary key.
     * Stores JWT ID (jti), not the whole refresh token.
     */
    @Id
    private String id;

    /*
     * Actual refresh token.
     */
    @Indexed
    private String token;
    
    @Indexed
    private UUID userId;

    private String email;
    
    @Indexed
    private String deviceId;

    @TimeToLive
    private Long expiration;

    public RefreshToken() {
    }

    public RefreshToken(
            String id,
            String token,
            UUID userId,
            String email,
            String deviceId,
            Long expiration) {

        this.id = id;
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.deviceId = deviceId;
        this.expiration = expiration;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

}