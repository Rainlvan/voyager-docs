package com.voyager.docs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backup_runs")
public class BackupRun {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BackupRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private BackupTriggerType triggerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by")
    private AppUser startedBy;

    @Column(name = "backup_filename", length = 500)
    private String backupFilename;

    @Column(name = "backup_path", length = 1000)
    private String backupPath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(length = 64)
    private String sha256;

    @Column(name = "object_count", nullable = false)
    private int objectCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "manifest_json")
    private String manifestJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BackupRunStatus getStatus() {
        return status;
    }

    public void setStatus(BackupRunStatus status) {
        this.status = status;
    }

    public BackupTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(BackupTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public AppUser getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(AppUser startedBy) {
        this.startedBy = startedBy;
    }

    public String getBackupFilename() {
        return backupFilename;
    }

    public void setBackupFilename(String backupFilename) {
        this.backupFilename = backupFilename;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
