package com.microservice.user.security.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.microservice.user.entity.User;

@Service
public class JwtServiceImpl implements JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    public JwtServiceImpl(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            JwtProperties jwtProperties) {

        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public String generateAccessToken(User user) {

        Instant issuedAt = Instant.now();

        Instant expiresAt =
                issuedAt.plus(
                        jwtProperties.getAccessTokenExpiration());

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(jwtProperties.getIssuer())
                        .subject(user.getUserId().toString())
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .claim("role", user.getRole().name())
                        .build();

        return jwtEncoder
                .encode(
                        JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    @Override
    public UUID extractUserId(String token) {

        Jwt jwt =
                jwtDecoder.decode(token);

        return UUID.fromString(
                jwt.getSubject());
    }

    @Override
    public Duration getAccessTokenExpiration() {

        return jwtProperties
                .getAccessTokenExpiration();
    }
}