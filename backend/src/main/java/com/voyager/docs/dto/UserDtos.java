package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record ManagedUserResponse(
            Long id,
            String username,
            String displayName,
            String role,
            boolean enabled,
            boolean deleted,
            String avatarUrl,
            String createdAt,
            String updatedAt,
            String deletedAt) {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_\\-.]{3,80}$") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 120) String password) {
    }

    public record UpdateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_\\-.]{3,80}$") String username,
            @NotBlank @Size(max = 120) String displayName,
            @Size(min = 8, max = 120) String password,
            boolean enabled) {
    }

    public record EnabledRequest(boolean enabled) {
    }
}
