package com.microservice.one.identity.redis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository
        extends CrudRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserId(UUID userId);

    Optional<RefreshToken> findByUserIdAndDeviceId(
            UUID userId,
            String deviceId);

    default void deleteByUserId(UUID userId) {
        findByUserId(userId)
                .forEach(token -> deleteById(token.getId()));
    }

    default void deleteByUserIdAndDeviceId(
            UUID userId,
            String deviceId) {

        findByUserIdAndDeviceId(userId, deviceId)
                .ifPresent(token -> deleteById(token.getId()));
    }
}