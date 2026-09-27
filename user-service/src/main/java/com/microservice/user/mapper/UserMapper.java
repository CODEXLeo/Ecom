package com.microservice.user.mapper;

import org.springframework.stereotype.Component;
import com.microservice.user.dto.request.RegisterRequest;
import com.microservice.user.dto.response.LoginResponse;
import com.microservice.user.dto.response.RegisterResponse;
import com.microservice.user.entity.User;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest registerRequest, String encodedPassword, String normalizedEmail) {
        return User.create(registerRequest.firstName().trim(), registerRequest.lastName().trim(), normalizedEmail, encodedPassword);
    }

    public RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(user.getUserId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole());
    }

    public LoginResponse toLoginResponse(User user) {
        return new LoginResponse(user.getUserId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole());
    }
}
