package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserResponse(Long id, String username, String displayName, String role, boolean enabled, boolean deleted, String avatarUrl) {
    }

    public record LoginResponse(String token, UserResponse user) {
    }

    public record UpdateAdminProfileRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_\\-.]{3,80}$") String username,
            @NotBlank @Size(max = 120) String displayName,
            String currentPassword,
            @Size(min = 8, max = 120) String newPassword) {
    }
}
