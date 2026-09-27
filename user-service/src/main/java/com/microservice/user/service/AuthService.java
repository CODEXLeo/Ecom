package com.microservice.user.service;

import com.microservice.user.dto.request.LoginRequest;
import com.microservice.user.dto.request.RegisterRequest;
import com.microservice.user.dto.response.LoginResponse;
import com.microservice.user.dto.response.RegisterResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest registerRequest);

    LoginResponse login(LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse);

    void refresh(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse);

    void logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse);
}