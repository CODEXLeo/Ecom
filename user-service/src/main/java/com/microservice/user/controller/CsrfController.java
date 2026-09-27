package com.microservice.user.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "CSRF", description = "CSRF protection APIs")
public class CsrfController {
    @Operation(summary = "Get CSRF token",description = "Returns a CSRF token and causes the XSRF-TOKEN cookie to be issued")
    @GetMapping("/csrf")
    public CsrfToken csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        return csrfToken;
    }
}