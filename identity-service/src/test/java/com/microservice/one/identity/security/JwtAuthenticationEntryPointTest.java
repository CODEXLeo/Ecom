package com.microservice.one.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    private ObjectMapper objectMapper;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private AuthenticationException authenticationException;

    @BeforeEach
    void setUp() {

        objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules();

        authenticationEntryPoint =
                new JwtAuthenticationEntryPoint(
                        objectMapper);

        request =
                mock(HttpServletRequest.class);

        response =
                new MockHttpServletResponse();

        authenticationException =
                mock(AuthenticationException.class);
    }

    @Test
    void commence_shouldReturn401() throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/users/me");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                mockRequest,
                mockResponse,
                authenticationException);

        assertEquals(
                401,
                mockResponse.getStatus());
    }

    @Test
    void commence_shouldSetJsonContentType()
            throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/users/me");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                mockRequest,
                mockResponse,
                authenticationException);

        assertEquals(
                MediaType.APPLICATION_JSON_VALUE,
                mockResponse.getContentType());
    }

    @Test
    void commence_shouldWriteExpectedErrorResponse()
            throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/users/me");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                mockRequest,
                mockResponse,
                authenticationException);

        String responseBody =
                mockResponse.getContentAsString(
                        StandardCharsets.UTF_8);

        assertNotNull(responseBody);

        assertTrue(
                responseBody.contains(
                        "Authentication is required to access this resource."));

        JsonNode json =
                objectMapper.readTree(responseBody);

        assertEquals(
                401,
                json.get("status").asInt());

        assertEquals(
                "Unauthorized",
                json.get("error").asText());

        assertEquals(
                "Authentication is required to access this resource.",
                json.get("message").asText());

        assertEquals(
                "/api/v1/users/me",
                json.get("path").asText());

        assertTrue(
                json.get("validationErrors").isArray());

        assertEquals(
                0,
                json.get("validationErrors").size());

        assertNotNull(
                json.get("timestamp"));
    }
}