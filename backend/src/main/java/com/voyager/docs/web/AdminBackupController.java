package com.voyager.docs.web;

import com.voyager.docs.dto.BackupDtos;
import com.voyager.docs.service.BackupService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBackupController {
    private final BackupService backupService;

    public AdminBackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/settings")
    public BackupDtos.BackupSettingsResponse settings() {
        return backupService.getSettings();
    }

    @PutMapping("/settings")
    public BackupDtos.BackupSettingsResponse updateSettings(@Valid @RequestBody BackupDtos.UpdateBackupSettingsRequest request) {
        return backupService.updateSettings(request);
    }

    @GetMapping
    public BackupDtos.BackupListResponse list() {
        return backupService.listBackups();
    }

    @PostMapping
    public BackupDtos.BackupRunResponse create() {
        return backupService.createManualBackup();
    }

    @PostMapping("/{id}/restore")
    public BackupDtos.BackupRunResponse restore(
            @PathVariable UUID id,
            @Valid @RequestBody BackupDtos.RestoreBackupRequest request) {
        return backupService.restoreBackup(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        backupService.deleteBackup(id);
    }
}
