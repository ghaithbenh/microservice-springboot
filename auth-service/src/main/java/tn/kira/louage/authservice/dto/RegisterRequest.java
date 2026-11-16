package tn.kira.louage.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.kira.louage.authservice.entity.Role;

public record RegisterRequest(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 6, message = "password must be at least 6 characters")
        String password,

        @NotNull(message = "role is required")
        Role role) {
}
