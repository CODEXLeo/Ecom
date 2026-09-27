package com.microservice.one.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.one.identity.jwt.JwtAuthenticationFilter;
import com.microservice.one.identity.jwt.JwtService;
import com.microservice.one.identity.security.JwtAccessDeniedHandler;
import com.microservice.one.identity.security.JwtAuthenticationEntryPoint;
import com.microservice.one.identity.service.AuthenticationService;
import com.microservice.one.identity.service.UserService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class SecurityConfigTest {


    @Autowired
    private MockMvc mockMvc;


    /*
     * ============================================================
     * CONTROLLER DEPENDENCIES
     * ============================================================
     */

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private UserService userService;


    /*
     * ============================================================
     * SECURITY DEPENDENCIES
     * ============================================================
     */

    @MockitoBean
    private AuthenticationProvider authenticationProvider;


    /*
     * ============================================================
     * JWT FILTER DEPENDENCIES
     *
     * IMPORTANT:
     *
     * JwtAuthenticationFilter itself is REAL.
     *
     * Only its dependencies are mocked.
     * ============================================================
     */

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;


    /*
     * ============================================================
     * AUTHENTICATION ENTRY POINT DEPENDENCY
     * ============================================================
     *
     * JwtAuthenticationEntryPoint is REAL.
     *
     * It requires ObjectMapper, which is supplied by the test
     * context in the normal application configuration.
     *
     * We do not mock JwtAuthenticationEntryPoint itself because
     * doing so would prevent it from setting HTTP 401.
     */

    @MockitoBean
    private ObjectMapper objectMapper;


    /*
     * ============================================================
     * PUBLIC AUTHENTICATION ENDPOINTS
     * ============================================================
     */

    @Test
    void register_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "security-test@example.com",
                                            "password": "Password@123",
                                            "firstName": "Security",
                                            "lastName": "Test",
                                            "deviceId": "test-device"
                                        }
                                        """))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    @Test
    void login_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "security-test@example.com",
                                            "password": "Password@123",
                                            "deviceId": "test-device"
                                        }
                                        """))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    @Test
    void refresh_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "refreshToken": "invalid-refresh-token",
                                            "deviceId": "security-test-device"
                                        }
                                        """))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    @Test
    void logout_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "refreshToken": "invalid-refresh-token",
                                            "deviceId": "security-test-device"
                                        }
                                        """))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    /*
     * ============================================================
     * SWAGGER / OPENAPI
     * ============================================================
     */

    @Test
    void swaggerUi_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        get("/swagger-ui/index.html"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    @Test
    void openApi_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        get("/v3/api-docs"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    /*
     * ============================================================
     * ACTUATOR HEALTH
     * ============================================================
     */

    @Test
    void actuatorHealth_shouldNotBeRejectedAsUnauthenticated()
            throws Exception {

        int status =
                mockMvc.perform(
                        get("/actuator/health"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertNotEquals(401, status);
    }


    /*
     * ============================================================
     * PROTECTED USER ENDPOINT
     * ============================================================
     */

    @Test
    void userProfile_shouldRequireAuthentication()
            throws Exception {

        int status =
                mockMvc.perform(
                        get("/api/v1/users/me"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertEquals(401, status);
    }


    /*
     * ============================================================
     * UNKNOWN ENDPOINT
     * ============================================================
     */

    @Test
    void unknownEndpoint_shouldRequireAuthentication()
            throws Exception {

        int status =
                mockMvc.perform(
                        get("/api/v1/protected-resource"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertEquals(401, status);
    }
}