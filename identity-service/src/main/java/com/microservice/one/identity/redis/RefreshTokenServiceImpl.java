package com.microservice.one.identity.redis;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository) {
    	this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public void save(
            String id,
            String token,
            UUID userId,
            String email,
            String deviceId,
            long expirationSeconds) {

        RefreshToken refreshToken =
                new RefreshToken(
                        id,
                        token,
                        userId,
                        email,
                        deviceId,
                        expirationSeconds);

        refreshTokenRepository.save(refreshToken);

        LOGGER.info(
                "Stored refresh token session id={} user={} device={}",
                id,
                email,
                deviceId);

    }

    @Override
    public RefreshToken findByToken(
            String token) {

        return refreshTokenRepository
                .findByToken(token)
                .orElse(null);

    }

    @Override
    public boolean exists(
            String token) {

        return refreshTokenRepository
                .findByToken(token)
                .isPresent();

    }

    @Override
    public void delete(
            String id) {

        refreshTokenRepository.deleteById(
                id);

        LOGGER.debug(
                "Deleted refresh token session id={}",
                id);

    }

    @Override
    public void deleteByUserAndDevice(
            UUID userId,
            String deviceId) {

        refreshTokenRepository.deleteByUserIdAndDeviceId(
                userId,
                deviceId);

        LOGGER.debug(
                "Deleted refresh tokens for user={} device={}",
                userId,
                deviceId);

    }

    @Override
    public void deleteAllByUser(
            UUID userId) {

        refreshTokenRepository.deleteByUserId(
                userId);

        LOGGER.debug(
                "Deleted all refresh tokens for user={}",
                userId);

    }

}