package com.microservice.user.security.jwt;

import java.time.Duration;
import java.util.UUID;

import com.microservice.user.entity.User;

public interface JwtService {

    String generateAccessToken(User user);

    UUID extractUserId(String token);

    Duration getAccessTokenExpiration();
}