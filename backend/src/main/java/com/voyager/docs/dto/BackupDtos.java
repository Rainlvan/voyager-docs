package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

public final class BackupDtos {
    private BackupDtos() {
    }

    public record BackupSettingsResponse(
            Long id,
            boolean enabled,
            String dailyTime,
            String updatedAt) {
    }

    public record UpdateBackupSettingsRequest(
            boolean enabled,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String dailyTime) {
    }

    public record BackupRunResponse(
            UUID id,
            String status,
            String triggerType,
            Long startedById,
            String startedBy,
            String backupFilename,
            long fileSize,
            String sha256,
            int objectCount,
            String errorMessage,
            String startedAt,
            String completedAt,
            String createdAt) {
    }

    public record BackupListResponse(
            List<BackupRunResponse> items,
            int totalCount,
            long totalBytes) {
    }

    public record RestoreBackupRequest(@NotBlank String currentPassword) {
    }

    public record MaintenanceResponse(
            boolean enabled,
            String reason,
            String startedAt,
            Long startedById,
            String startedBy) {
    }
}
