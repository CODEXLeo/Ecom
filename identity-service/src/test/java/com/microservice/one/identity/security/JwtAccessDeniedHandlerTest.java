package com.microservice.one.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAccessDeniedHandlerTest {

    private JwtAccessDeniedHandler accessDeniedHandler;

    private ObjectMapper objectMapper;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private AccessDeniedException accessDeniedException;

    @BeforeEach
    void setUp() {

        objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules();

        accessDeniedHandler =
                new JwtAccessDeniedHandler(objectMapper);

        request = mock(HttpServletRequest.class);

        response =
                new MockHttpServletResponse();

        accessDeniedException =
                mock(AccessDeniedException.class);
    }

    @Test
    void handle_shouldReturn403() throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/admin");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        accessDeniedHandler.handle(
                mockRequest,
                mockResponse,
                accessDeniedException);

        assertEquals(
                403,
                mockResponse.getStatus());
    }

    @Test
    void handle_shouldSetJsonContentType() throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/admin");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        accessDeniedHandler.handle(
                mockRequest,
                mockResponse,
                accessDeniedException);

        assertEquals(
                MediaType.APPLICATION_JSON_VALUE,
                mockResponse.getContentType());
    }

    @Test
    void handle_shouldWriteExpectedErrorResponse()
            throws Exception {

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest();

        mockRequest.setRequestURI(
                "/api/v1/admin");

        MockHttpServletResponse mockResponse =
                new MockHttpServletResponse();

        accessDeniedHandler.handle(
                mockRequest,
                mockResponse,
                accessDeniedException);

        String responseBody =
                mockResponse.getContentAsString(
                        StandardCharsets.UTF_8);

        assertNotNull(responseBody);

        assertTrue(
                responseBody.contains(
                        "You do not have permission to access this resource."));

        JsonNode json =
                objectMapper.readTree(responseBody);

        assertEquals(
                403,
                json.get("status").asInt());

        assertEquals(
                "Forbidden",
                json.get("error").asText());

        assertEquals(
                "You do not have permission to access this resource.",
                json.get("message").asText());

        assertEquals(
                "/api/v1/admin",
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