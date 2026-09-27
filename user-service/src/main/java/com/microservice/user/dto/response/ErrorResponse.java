package com.microservice.user.dto.response;

public record ErrorResponse(
        String detail,
        String code
) {
}