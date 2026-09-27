package com.microservice.one.identity.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Testcontainers
class RefreshTokenRepositoryTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(2));

    private static AnnotationConfigApplicationContext context;

    private static RefreshTokenRepository refreshTokenRepository;

    private UUID firstUserId;
    private UUID secondUserId;

    @BeforeAll
    static void startRedis() {
        REDIS.start();

        context = new AnnotationConfigApplicationContext();

        context.register(RedisTestConfiguration.class);

        context.refresh();

        refreshTokenRepository =
                context.getBean(RefreshTokenRepository.class);
    }

    @AfterAll
    static void stopRedis() {
        if (context != null) {
            context.close();
        }

        REDIS.stop();
    }

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();

        firstUserId = UUID.randomUUID();
        secondUserId = UUID.randomUUID();
    }

    @Test
    void saveAndFindByToken_shouldReturnRefreshToken() {

        RefreshToken refreshToken =
                createRefreshToken(
                        firstUserId,
                        "token-1",
                        "windows-desktop");

        refreshTokenRepository.save(refreshToken);

        var result =
                refreshTokenRepository.findByToken("token-1");

        assertThat(result)
                .isPresent();

        assertThat(result.get().getToken())
                .isEqualTo("token-1");

        assertThat(result.get().getUserId())
                .isEqualTo(firstUserId);

        assertThat(result.get().getDeviceId())
                .isEqualTo("windows-desktop");
    }

    @Test
    void findByToken_shouldReturnEmptyForUnknownToken() {

        var result =
                refreshTokenRepository.findByToken(
                        "unknown-token");

        assertThat(result)
                .isEmpty();
    }

    @Test
    void findByUserId_shouldReturnAllUserTokens() {

        RefreshToken firstToken =
                createRefreshToken(
                        firstUserId,
                        "token-1",
                        "windows-desktop");

        RefreshToken secondToken =
                createRefreshToken(
                        firstUserId,
                        "token-2",
                        "android-phone");

        RefreshToken otherUserToken =
                createRefreshToken(
                        secondUserId,
                        "token-3",
                        "windows-desktop");

        refreshTokenRepository.save(firstToken);
        refreshTokenRepository.save(secondToken);
        refreshTokenRepository.save(otherUserToken);

        List<RefreshToken> result =
                refreshTokenRepository.findByUserId(
                        firstUserId);

        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .extracting(RefreshToken::getToken)
                .containsExactlyInAnyOrder(
                        "token-1",
                        "token-2");
    }

    @Test
    void findByUserId_shouldReturnEmptyForUserWithoutTokens() {

        List<RefreshToken> result =
                refreshTokenRepository.findByUserId(
                        UUID.randomUUID());

        assertThat(result)
                .isEmpty();
    }

    @Test
    void findByUserIdAndDeviceId_shouldReturnMatchingToken() {

        RefreshToken windowsToken =
                createRefreshToken(
                        firstUserId,
                        "windows-token",
                        "windows-desktop");

        RefreshToken mobileToken =
                createRefreshToken(
                        firstUserId,
                        "mobile-token",
                        "android-phone");

        refreshTokenRepository.save(windowsToken);
        refreshTokenRepository.save(mobileToken);

        var result =
                refreshTokenRepository
                        .findByUserIdAndDeviceId(
                                firstUserId,
                                "windows-desktop");

        assertThat(result)
                .isPresent();

        assertThat(result.get().getToken())
                .isEqualTo("windows-token");

        assertThat(result.get().getDeviceId())
                .isEqualTo("windows-desktop");
    }

    @Test
    void findByUserIdAndDeviceId_shouldReturnEmptyForUnknownDevice() {

        RefreshToken refreshToken =
                createRefreshToken(
                        firstUserId,
                        "token-1",
                        "windows-desktop");

        refreshTokenRepository.save(refreshToken);

        var result =
                refreshTokenRepository
                        .findByUserIdAndDeviceId(
                                firstUserId,
                                "unknown-device");

        assertThat(result)
                .isEmpty();
    }

    @Test
    void deleteByUserId_shouldDeleteAllTokensForUser() {

        RefreshToken firstToken =
                createRefreshToken(
                        firstUserId,
                        "token-1",
                        "windows-desktop");

        RefreshToken secondToken =
                createRefreshToken(
                        firstUserId,
                        "token-2",
                        "android-phone");

        RefreshToken otherUserToken =
                createRefreshToken(
                        secondUserId,
                        "token-3",
                        "windows-desktop");

        refreshTokenRepository.save(firstToken);
        refreshTokenRepository.save(secondToken);
        refreshTokenRepository.save(otherUserToken);

        refreshTokenRepository.deleteByUserId(firstUserId);

        assertThat(
                refreshTokenRepository
                        .findByUserId(firstUserId))
                .isEmpty();

        assertThat(
                refreshTokenRepository
                        .findByToken("token-3"))
                .isPresent();
    }

    @Test
    void deleteByUserIdAndDeviceId_shouldDeleteOnlyMatchingDevice() {

        RefreshToken windowsToken =
                createRefreshToken(
                        firstUserId,
                        "windows-token",
                        "windows-desktop");

        RefreshToken mobileToken =
                createRefreshToken(
                        firstUserId,
                        "mobile-token",
                        "android-phone");

        refreshTokenRepository.save(windowsToken);
        refreshTokenRepository.save(mobileToken);

        refreshTokenRepository
                .deleteByUserIdAndDeviceId(
                        firstUserId,
                        "windows-desktop");

        assertThat(
                refreshTokenRepository
                        .findByToken("windows-token"))
                .isEmpty();

        assertThat(
                refreshTokenRepository
                        .findByToken("mobile-token"))
                .isPresent();
    }

    private RefreshToken createRefreshToken(
            UUID userId,
            String token,
            String deviceId) {

        RefreshToken refreshToken =
                new RefreshToken();

        refreshToken.setId(
                UUID.randomUUID().toString());

        refreshToken.setToken(token);

        refreshToken.setUserId(userId);

        refreshToken.setEmail(
                "swata@example.com");

        refreshToken.setDeviceId(deviceId);

        refreshToken.setExpiration(
                Duration.ofHours(1).getSeconds());

        return refreshToken;
    }

    @Configuration
    @EnableRedisRepositories(
            basePackageClasses = RefreshTokenRepository.class
    )
    static class RedisTestConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {

            return new LettuceConnectionFactory(
                    REDIS.getHost(),
                    REDIS.getMappedPort(6379));
        }

        @Bean
        RedisTemplate<String, Object> redisTemplate(
                RedisConnectionFactory connectionFactory) {

            RedisTemplate<String, Object> template =
                    new RedisTemplate<>();

            template.setConnectionFactory(
                    connectionFactory);

            template.setKeySerializer(
                    new StringRedisSerializer());

            template.setHashKeySerializer(
                    new StringRedisSerializer());

            template.setValueSerializer(
                    new GenericJackson2JsonRedisSerializer());

            template.setHashValueSerializer(
                    new GenericJackson2JsonRedisSerializer());

            template.afterPropertiesSet();

            return template;
        }
    }
}