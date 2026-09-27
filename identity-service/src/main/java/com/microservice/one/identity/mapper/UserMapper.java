package com.microservice.one.identity.mapper;

import org.springframework.stereotype.Component;

import com.microservice.one.identity.dto.request.RegisterRequest;
import com.microservice.one.identity.dto.request.UpdateUserRequest;
import com.microservice.one.identity.dto.response.UserResponse;
import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.enums.Role;

@Component
public class UserMapper {

    /**
     * Convert RegisterRequest DTO into User entity.
     *
     * Password must already be encoded before calling this method.
     */
    public User toEntity(RegisterRequest registerRequest, String encodedPassword) {
        User user = new User();
        user.setFirstName(registerRequest.firstName());
        user.setLastName(registerRequest.lastName());
        user.setEmail(registerRequest.email());
        user.setPasswordHash(encodedPassword);

        /*
         * Every newly registered user receives ROLE_USER.
         * Admins should only be created through a separate admin process.
         */
        user.setRole(Role.ROLE_USER);
        return user;
    }

    /**
     * Convert User entity into UserResponse DTO.
     */
    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
    
    public void updateUser(User user, UpdateUserRequest updateUserRequest) {
        user.setFirstName(updateUserRequest.firstName());
        user.setLastName(updateUserRequest.lastName());
    }

}