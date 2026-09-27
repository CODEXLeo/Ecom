package com.microservice.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.microservice.user.validation.PasswordPolicy;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password must not be blank")
        String currentPassword,

        @NotBlank(message = "New password must not be blank")
        @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
        @Pattern(regexp = PasswordPolicy.REGEX, message = "Password must contain lowercase, uppercase, digit and special character"
        )
        String newPassword
) {
}
