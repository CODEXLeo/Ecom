package com.microservice.one.identity.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private UUID userId;

    @BeforeEach
    void setUp() {

        refreshTokenService =
                new RefreshTokenServiceImpl(
                        refreshTokenRepository);

        userId = UUID.randomUUID();
    }

    @Test
    void save_shouldCreateAndPersistRefreshToken() {

        String id = "jti-123";
        String token = "refresh-token-123";
        String email = "swata@example.com";
        String deviceId = "windows-desktop";
        long expirationSeconds = 604800L;

        refreshTokenService.save(
                id,
                token,
                userId,
                email,
                deviceId,
                expirationSeconds);

        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(
                        RefreshToken.class);

        verify(refreshTokenRepository)
                .save(captor.capture());

        RefreshToken savedToken =
                captor.getValue();

        assertThat(savedToken.getId())
                .isEqualTo(id);

        assertThat(savedToken.getToken())
                .isEqualTo(token);

        assertThat(savedToken.getUserId())
                .isEqualTo(userId);

        assertThat(savedToken.getEmail())
                .isEqualTo(email);

        assertThat(savedToken.getDeviceId())
                .isEqualTo(deviceId);

        assertThat(savedToken.getExpiration())
                .isEqualTo(expirationSeconds);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void findByToken_shouldReturnRefreshTokenWhenFound() {

        String token = "refresh-token-123";

        RefreshToken refreshToken =
                createRefreshToken(
                        "jti-123",
                        token,
                        userId,
                        "swata@example.com",
                        "windows-desktop",
                        604800L);

        when(refreshTokenRepository.findByToken(token))
                .thenReturn(
                        Optional.of(refreshToken));

        RefreshToken result =
                refreshTokenService.findByToken(
                        token);

        assertThat(result)
                .isSameAs(refreshToken);

        verify(refreshTokenRepository)
                .findByToken(token);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void findByToken_shouldReturnNullWhenTokenDoesNotExist() {

        String token = "unknown-token";

        when(refreshTokenRepository.findByToken(token))
                .thenReturn(Optional.empty());

        RefreshToken result =
                refreshTokenService.findByToken(
                        token);

        assertThat(result)
                .isNull();

        verify(refreshTokenRepository)
                .findByToken(token);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void exists_shouldReturnTrueWhenTokenExists() {

        String token = "refresh-token-123";

        RefreshToken refreshToken =
                createRefreshToken(
                        "jti-123",
                        token,
                        userId,
                        "swata@example.com",
                        "windows-desktop",
                        604800L);

        when(refreshTokenRepository.findByToken(token))
                .thenReturn(
                        Optional.of(refreshToken));

        boolean result =
                refreshTokenService.exists(token);

        assertThat(result)
                .isTrue();

        verify(refreshTokenRepository)
                .findByToken(token);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void exists_shouldReturnFalseWhenTokenDoesNotExist() {

        String token = "unknown-token";

        when(refreshTokenRepository.findByToken(token))
                .thenReturn(Optional.empty());

        boolean result =
                refreshTokenService.exists(token);

        assertThat(result)
                .isFalse();

        verify(refreshTokenRepository)
                .findByToken(token);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void delete_shouldDeleteTokenById() {

        String id = "jti-123";

        refreshTokenService.delete(id);

        verify(refreshTokenRepository)
                .deleteById(id);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void deleteByUserAndDevice_shouldDeleteMatchingDeviceTokens() {

        String deviceId = "windows-desktop";

        refreshTokenService.deleteByUserAndDevice(
                userId,
                deviceId);

        verify(refreshTokenRepository)
                .deleteByUserIdAndDeviceId(
                        userId,
                        deviceId);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void deleteAllByUser_shouldDeleteAllUserTokens() {

        refreshTokenService.deleteAllByUser(
                userId);

        verify(refreshTokenRepository)
                .deleteByUserId(userId);

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    @Test
    void save_shouldNotPerformAnyReadOrDeleteOperation() {

        refreshTokenService.save(
                "jti-123",
                "refresh-token-123",
                userId,
                "swata@example.com",
                "windows-desktop",
                604800L);

        verify(refreshTokenRepository)
                .save(any(RefreshToken.class));

        verify(refreshTokenRepository, never())
                .findByToken(any());

        verify(refreshTokenRepository, never())
                .deleteById(any());

        verify(refreshTokenRepository, never())
                .deleteByUserId(any());

        verify(refreshTokenRepository, never())
                .deleteByUserIdAndDeviceId(
                        any(),
                        any());

        verifyNoMoreInteractions(
                refreshTokenRepository);
    }

    private RefreshToken createRefreshToken(
            String id,
            String token,
            UUID userId,
            String email,
            String deviceId,
            long expirationSeconds) {

        return new RefreshToken(
                id,
                token,
                userId,
                email,
                deviceId,
                expirationSeconds);
    }
}