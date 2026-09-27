package com.microservice.one.identity.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.redis.RefreshTokenRepository;
import com.microservice.one.identity.service.AuthenticationService;
import com.microservice.one.identity.util.TestDataFactory;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationControllerIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                    .withStartupTimeout(Duration.ofMinutes(3))
                    .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8")
                    .withExposedPorts(6379)
                    .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername);

        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword);

        registry.add(
                "spring.data.redis.host",
                REDIS::getHost);

        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379));

        registry.add(
                "jwt.access-secret",
                () -> "ThisIsAVeryLongAccessSecretKeyForTesting123456789");

        registry.add(
                "jwt.refresh-secret",
                () -> "ThisIsAVeryLongRefreshSecretKeyForTesting123456789");

        registry.add(
                "jwt.access-expiration",
                () -> "900000");

        registry.add(
                "jwt.refresh-expiration",
                () -> "604800000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {

        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    /*
     * ============================================================
     * REGISTER
     * ============================================================
     */

    @Test
    void register_shouldReturnCreated() throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    void register_shouldRejectDuplicateEmail() throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory.registerRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shouldRejectInvalidEmail() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.RegisterRequest(
                        "Swatantra",
                        "Naskar",
                        "invalid-email",
                        "Password@123");

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldRejectWeakPassword() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.RegisterRequest(
                        "Swatantra",
                        "Naskar",
                        "swata@example.com",
                        "password");

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldRejectBlankFirstName() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.RegisterRequest(
                        "",
                        "Naskar",
                        "swata@example.com",
                        "Password@123");

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldRejectBlankLastName() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.RegisterRequest(
                        "Swatantra",
                        "",
                        "swata@example.com",
                        "Password@123");

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /*
     * ============================================================
     * LOGIN
     * ============================================================
     */

    @Test
    void login_shouldReturnOk() throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.loginRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void login_shouldRejectWrongPassword() throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var request =
                new com.microservice.one.identity.dto.request.LoginRequest(
                        "swata@example.com",
                        "WrongPassword@123",
                        "windows-desktop");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldRejectUnknownUser() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.LoginRequest(
                        "unknown@example.com",
                        "Password@123",
                        "windows-desktop");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldRejectBlankPassword() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.LoginRequest(
                        "swata@example.com",
                        "",
                        "windows-desktop");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldRejectBlankEmail() throws Exception {

        var request =
                new com.microservice.one.identity.dto.request.LoginRequest(
                        "",
                        "Password@123",
                        "windows-desktop");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /*
     * ============================================================
     * REFRESH TOKEN
     * ============================================================
     */

    @Test
    void refresh_shouldReturnOkAndRotateRefreshToken()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var loginResult =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        TestDataFactory.loginRequest())))
                        .andExpect(status().isOk())
                        .andReturn();

        String oldRefreshToken =
                extractRefreshToken(loginResult);

        var refreshRequest =
                TestDataFactory.refreshTokenRequest(
                        oldRefreshToken);

        var refreshResult =
                mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        refreshRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        String newRefreshToken =
                extractRefreshToken(refreshResult);

        org.assertj.core.api.Assertions.assertThat(
                newRefreshToken)
                .isNotBlank()
                .isNotEqualTo(oldRefreshToken);

        org.assertj.core.api.Assertions.assertThat(
                refreshResult.getResponse().getContentAsString())
                .contains("\"accessToken\"")
                .contains("\"refreshToken\"");
    }

    @Test
    void refresh_shouldRejectInvalidToken()
            throws Exception {

        var request =
                TestDataFactory.refreshTokenRequest(
                        "invalid-refresh-token");

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldRejectWrongDevice()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var loginResult =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        TestDataFactory.loginRequest())))
                        .andExpect(status().isOk())
                        .andReturn();

        String refreshToken =
                extractRefreshToken(loginResult);

        var wrongDeviceRequest =
                TestDataFactory.mobileRefreshTokenRequest(
                        refreshToken);

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                wrongDeviceRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldRejectReplayedToken()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var loginResult =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        TestDataFactory.loginRequest())))
                        .andExpect(status().isOk())
                        .andReturn();

        String refreshToken =
                extractRefreshToken(loginResult);

        var refreshRequest =
                TestDataFactory.refreshTokenRequest(
                        refreshToken);

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                refreshRequest)))
                .andExpect(status().isOk());

        /*
         * The original refresh token has been rotated
         * and therefore must no longer be usable.
         */
        mockMvc.perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    /*
     * ============================================================
     * LOGOUT
     * ============================================================
     */

    @Test
    void logout_shouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var loginResult =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        TestDataFactory.loginRequest())))
                        .andExpect(status().isOk())
                        .andReturn();

        String refreshToken =
                extractRefreshToken(loginResult);

        var logoutRequest =
                TestDataFactory.refreshTokenRequest(
                        refreshToken);

        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                logoutRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_shouldRejectWrongDevice()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.registerRequest())))
                .andExpect(status().isCreated());

        var loginResult =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        TestDataFactory.loginRequest())))
                        .andExpect(status().isOk())
                        .andReturn();

        String refreshToken =
                extractRefreshToken(loginResult);

        var wrongDeviceRequest =
                TestDataFactory.mobileRefreshTokenRequest(
                        refreshToken);

        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                wrongDeviceRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldBeIdempotentForInvalidToken()
            throws Exception {

        var logoutRequest =
                TestDataFactory.refreshTokenRequest(
                        "invalid-refresh-token");

        /*
         * AuthenticationService.logout() deliberately treats
         * an unparseable refresh token as an idempotent logout.
         */
        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                logoutRequest)))
                .andExpect(status().isNoContent());
    }

    /*
     * ============================================================
     * TEST HELPERS
     * ============================================================
     */

    private String extractRefreshToken(
            org.springframework.test.web.servlet.MvcResult result)
            throws Exception {

        var json =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString());

        return json.get("refreshToken").asText();
    }
}