package com.microservice.one.identity.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.one.identity.exception.ApiErrorResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAccessDeniedHandler
        implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

    }

    @Override
    public void handle(

            HttpServletRequest request,

            HttpServletResponse response,

            AccessDeniedException accessDeniedException)

            throws IOException, ServletException {

        ApiErrorResponse apiErrorResponse =
                new ApiErrorResponse(

                        LocalDateTime.now(),

                        HttpStatus.FORBIDDEN.value(),

                        HttpStatus.FORBIDDEN.getReasonPhrase(),

                        "You do not have permission to access this resource.",

                        request.getRequestURI(),

                        List.of());

        response.setStatus(
                HttpStatus.FORBIDDEN.value());

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(

                response.getOutputStream(),

                apiErrorResponse);

    }

}