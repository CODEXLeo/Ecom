package com.microservice.one.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.redis.RefreshTokenRepository;
import com.microservice.one.identity.repository.UserRepository;
import com.microservice.one.identity.service.AuthenticationService;
import com.microservice.one.identity.util.TestDataFactory;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIntegrationTest {

    /*
     * ============================================================
     * TEST CONTAINERS
     * ============================================================
     */

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withTmpFs(
                            Map.of(
                                    "/var/lib/mysql",
                                    "rw"))
                    .withStartupTimeout(
                            Duration.ofMinutes(3))
                    .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8")
                    .withExposedPorts(6379)
                    .withReuse(true);

    /*
     * ============================================================
     * DYNAMIC TEST PROPERTIES
     * ============================================================
     */

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
                () ->
                        "ThisIsAVeryLongAccessSecretKeyForTesting123456789");

        registry.add(
                "jwt.refresh-secret",
                () ->
                        "ThisIsAVeryLongRefreshSecretKeyForTesting123456789");

        registry.add(
                "jwt.access-expiration",
                () -> "900000");

        registry.add(
                "jwt.refresh-expiration",
                () -> "604800000");
    }

    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

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

    /*
     * ============================================================
     * SETUP
     * ============================================================
     */

    @BeforeEach
    void setUp() {

        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    /*
     * ============================================================
     * GET CURRENT USER
     * ============================================================
     */

    @Test
    void getCurrentUser_shouldReturnOk()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.userId")
                                .isNotEmpty())
                .andExpect(
                        jsonPath("$.firstName")
                                .value("Swatantra"))
                .andExpect(
                        jsonPath("$.lastName")
                                .value("Naskar"))
                .andExpect(
                        jsonPath("$.email")
                                .value("swata@example.com"))
                .andExpect(
                        jsonPath("$.role")
                                .value("ROLE_USER"))
                .andExpect(
                        jsonPath("$.createdAt")
                                .isNotEmpty())
                .andExpect(
                        jsonPath("$.updatedAt")
                                .isNotEmpty());
    }

    @Test
    void getCurrentUser_shouldRejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/users/me"))
                .andExpect(
                        status().isUnauthorized());
    }

    @Test
    void getCurrentUser_shouldAcceptValidAccessToken()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.email")
                                .value("swata@example.com"));
    }

    @Test
    void getCurrentUser_shouldRejectInvalidAccessToken()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer invalid-access-token"))
                .andExpect(
                        status().isUnauthorized());
    }

    /*
     * ============================================================
     * UPDATE CURRENT USER
     * ============================================================
     */

    @Test
    void updateCurrentUser_shouldReturnOk()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory
                                                .updateUserRequest())))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.firstName")
                                .value("UpdatedFirstName"))
                .andExpect(
                        jsonPath("$.lastName")
                                .value("UpdatedLastName"))
                .andExpect(
                        jsonPath("$.email")
                                .value("swata@example.com"))
                .andExpect(
                        jsonPath("$.role")
                                .value("ROLE_USER"));
    }

    @Test
    void updateCurrentUser_shouldRejectBlankFirstName()
            throws Exception {

        String accessToken =
                authenticateUser();

        UpdateUserRequest invalidRequest =
                new UpdateUserRequest(
                        "",
                        "UpdatedLastName");

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        invalidRequest)))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void updateCurrentUser_shouldRejectBlankLastName()
            throws Exception {

        String accessToken =
                authenticateUser();

        UpdateUserRequest invalidRequest =
                new UpdateUserRequest(
                        "UpdatedFirstName",
                        "");

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        invalidRequest)))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void updateCurrentUser_shouldRejectTooShortFirstName()
            throws Exception {

        String accessToken =
                authenticateUser();

        UpdateUserRequest invalidRequest =
                new UpdateUserRequest(
                        "A",
                        "UpdatedLastName");

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        invalidRequest)))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void updateCurrentUser_shouldRejectTooShortLastName()
            throws Exception {

        String accessToken =
                authenticateUser();

        UpdateUserRequest invalidRequest =
                new UpdateUserRequest(
                        "UpdatedFirstName",
                        "N");

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        invalidRequest)))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void updateCurrentUser_shouldRejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(
                put("/api/v1/users/me")
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory
                                                .updateUserRequest())))
                .andExpect(
                        status().isUnauthorized());
    }

    @Test
    void updateCurrentUser_shouldAcceptValidAccessToken()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                put("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        TestDataFactory
                                                .updateUserRequest())))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.firstName")
                                .value("UpdatedFirstName"))
                .andExpect(
                        jsonPath("$.lastName")
                                .value("UpdatedLastName"));
    }

    /*
     * ============================================================
     * DELETE CURRENT USER
     * ============================================================
     */

    @Test
    void deleteCurrentUser_shouldReturnNoContent()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                delete("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken))
                .andExpect(
                        status().isNoContent());

        /*
         * Verify that the account was actually deleted
         * from the database.
         */

        assertThat(
                userRepository.existsByEmail(
                        "swata@example.com"))
                .isFalse();
    }

    @Test
    void deleteCurrentUser_shouldRejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(
                delete("/api/v1/users/me"))
                .andExpect(
                        status().isUnauthorized());
    }

    @Test
    void deleteCurrentUser_shouldAcceptValidAccessToken()
            throws Exception {

        String accessToken =
                authenticateUser();

        mockMvc.perform(
                delete("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken))
                .andExpect(
                        status().isNoContent());

        assertThat(
                userRepository.existsByEmail(
                        "swata@example.com"))
                .isFalse();
    }

    /*
     * ============================================================
     * TEST AUTHENTICATION HELPER
     * ============================================================
     */

    private String authenticateUser()
            throws Exception {

        User user =
                TestDataFactory.user();

        userRepository.saveAndFlush(user);

        var authResponse =
                authenticationService.login(
                        TestDataFactory.loginRequest());

        /*
         * Extract accessToken from AuthResponse without
         * depending on the record accessor name.
         */

        String responseJson =
                objectMapper.writeValueAsString(
                        authResponse);

        JsonNode jsonNode =
                objectMapper.readTree(
                        responseJson);

        return jsonNode
                .get("accessToken")
                .asText();
    }
}