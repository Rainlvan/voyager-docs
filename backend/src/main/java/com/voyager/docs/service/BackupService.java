package com.voyager.docs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.config.AppProperties;
import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.BackupRun;
import com.voyager.docs.domain.BackupRunStatus;
import com.voyager.docs.domain.BackupSetting;
import com.voyager.docs.domain.BackupTriggerType;
import com.voyager.docs.domain.UserRole;
import com.voyager.docs.dto.BackupDtos;
import com.voyager.docs.repository.BackupRunRepository;
import com.voyager.docs.repository.BackupSettingRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BackupService {
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String POSTGRES_ENTRY = "postgres.dump";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final Object backupLock = new Object();

    private final BackupSettingRepository backupSettings;
    private final BackupRunRepository backupRuns;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storage;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final MaintenanceService maintenanceService;
    private final DocumentService documentService;
    private final AuditService auditService;

    public BackupService(
            BackupSettingRepository backupSettings,
            BackupRunRepository backupRuns,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            StorageService storage,
            AppProperties properties,
            ObjectMapper objectMapper,
            MaintenanceService maintenanceService,
            DocumentService documentService,
            AuditService auditService) {
        this.backupSettings = backupSettings;
        this.backupRuns = backupRuns;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.maintenanceService = maintenanceService;
        this.documentService = documentService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BackupDtos.BackupSettingsResponse getSettings() {
        requireAdmin();
        return toSettingsResponse(requireSettings());
    }

    @Transactional
    public BackupDtos.BackupSettingsResponse updateSettings(BackupDtos.UpdateBackupSettingsRequest request) {
        AppUser admin = requireAdmin();
        BackupSetting setting = requireSettings();
        setting.setEnabled(request.enabled());
        setting.setDailyTime(request.dailyTime());
        setting.setUpdatedBy(admin);
        BackupSetting saved = backupSettings.save(setting);
        auditService.recordForUser(admin, "BACKUP_SETTINGS_UPDATE", "BACKUP_SETTINGS", String.valueOf(saved.getId()), true,
                "Updated backup schedule");
        return toSettingsResponse(saved);
    }

    public BackupDtos.BackupListResponse listBackups() {
        requireAdmin();
        synchronizeBackupFiles();
        List<BackupRun> runs = backupRuns.findAllByOrderByCreatedAtDesc();
        long totalBytes = backupRuns.findByStatusIn(List.of(BackupRunStatus.SUCCEEDED, BackupRunStatus.RESTORED)).stream()
                .mapToLong(BackupRun::getFileSize)
                .sum();
        return new BackupDtos.BackupListResponse(
                runs.stream().map(this::toRunResponse).toList(),
                runs.size(),
                totalBytes);
    }

    public BackupDtos.BackupRunResponse createManualBackup() {
        AppUser admin = requireAdmin();
        maintenanceService.requireAvailableForWrites();
        BackupRun created = createBackup(BackupTriggerType.MANUAL, admin, true);
        return toRunResponse(backupRuns.findById(created.getId()).orElse(created));
    }

    public void deleteBackup(UUID id) {
        AppUser admin = requireAdmin();
        BackupRun run = backupRuns.findById(id).orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        if (run.getStatus() == BackupRunStatus.RUNNING || run.getStatus() == BackupRunStatus.RESTORING) {
            throw new IllegalArgumentException("Running backups cannot be deleted");
        }
        if (StringUtils.hasText(run.getBackupPath())) {
            try {
                Files.deleteIfExists(Path.of(run.getBackupPath()));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to delete backup file", exception);
            }
        }
        backupRuns.delete(run);
        auditService.recordForUser(admin, "BACKUP_DELETE", "BACKUP", id.toString(), true, "Deleted backup record and archive");
    }

    public BackupDtos.BackupRunResponse restoreBackup(UUID id, BackupDtos.RestoreBackupRequest request) {
        AppUser admin = requireAdmin();
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            auditService.recordForUser(admin, "BACKUP_RESTORE", "BACKUP", id.toString(), false, "Current password verification failed");
            throw new BadCredentialsException("Current password is incorrect");
        }
        maintenanceService.requireAvailableForWrites();
        BackupRun selected = backupRuns.findById(id).orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        if (selected.getStatus() != BackupRunStatus.SUCCEEDED && selected.getStatus() != BackupRunStatus.RESTORED) {
            throw new IllegalArgumentException("Only successful backups can be restored");
        }
        Path archive = requireBackupArchive(selected);

        createBackup(BackupTriggerType.PRE_RESTORE, admin, true);
        maintenanceService.enter("Restoring backup " + selected.getBackupFilename(), admin);
        auditService.recordForUser(admin, "BACKUP_RESTORE_START", "BACKUP", id.toString(), true, "System entered maintenance mode");
        try {
            restoreArchive(archive);
            synchronizeBackupFiles();
            documentService.enqueueAllVisibleForReindexAfterRestore();
            maintenanceService.exit();
            auditService.recordForUser(admin, "BACKUP_RESTORE", "BACKUP", id.toString(), true, "Restored backup and queued index rebuild");
            return toRunResponse(backupRuns.findById(id).orElse(selected));
        } catch (RuntimeException exception) {
            maintenanceService.exit();
            auditService.recordForUser(admin, "BACKUP_RESTORE", "BACKUP", id.toString(), false, exception.getMessage());
            throw exception;
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void runScheduledBackupIfDue() {
        BackupSetting setting = backupSettings.findAll().stream().findFirst().orElse(null);
        if (setting == null || !setting.isEnabled() || maintenanceService.isEnabled()) {
            return;
        }
        String now = DateTimeFormatter.ofPattern("HH:mm").format(java.time.LocalTime.now());
        if (!setting.getDailyTime().equals(now)) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        if (backupRuns.existsByTriggerTypeAndCreatedAtBetween(BackupTriggerType.SCHEDULED, start, end)) {
            return;
        }
        try {
            createBackup(BackupTriggerType.SCHEDULED, null, false);
        } catch (RuntimeException exception) {
            auditService.recordForUser(null, "BACKUP_SCHEDULED", "BACKUP", null, false, exception.getMessage());
        }
    }

    public BackupRun createBackup(BackupTriggerType triggerType, AppUser startedBy, boolean throwOnFailure) {
        synchronized (backupLock) {
            synchronizeBackupFiles();
            if (backupRuns.existsByStatus(BackupRunStatus.RUNNING)) {
                throw new IllegalStateException("A backup is already running");
            }
            BackupRun run = new BackupRun();
            run.setId(UUID.randomUUID());
            run.setStatus(BackupRunStatus.RUNNING);
            run.setTriggerType(triggerType);
            run.setStartedBy(startedBy);
            run = backupRuns.saveAndFlush(run);
            try {
                BackupArtifact artifact = buildBackupArchive(run);
                run.setStatus(BackupRunStatus.SUCCEEDED);
                run.setBackupFilename(artifact.filename());
                run.setBackupPath(artifact.path().toString());
                run.setFileSize(artifact.fileSize());
                run.setSha256(artifact.sha256());
                run.setObjectCount(artifact.objectCount());
                run.setManifestJson(artifact.manifestJson());
                run.setCompletedAt(Instant.now());
                BackupRun saved = backupRuns.save(run);
                auditService.recordForUser(startedBy, "BACKUP_CREATE", "BACKUP", saved.getId().toString(), true,
                        triggerType.name() + " full backup created");
                return saved;
            } catch (RuntimeException exception) {
                run.setStatus(BackupRunStatus.FAILED);
                run.setErrorMessage(trim(exception.getMessage(), 4000));
                run.setCompletedAt(Instant.now());
                BackupRun failed = backupRuns.save(run);
                auditService.recordForUser(startedBy, "BACKUP_CREATE", "BACKUP", failed.getId().toString(), false,
                        exception.getMessage());
                if (throwOnFailure) {
                    throw exception;
                }
                return failed;
            }
        }
    }

    private BackupArtifact buildBackupArchive(BackupRun run) {
        try {
            Path backupDirectory = backupDirectory();
            Files.createDirectories(backupDirectory);
            String filename = "voyager-full-backup-" + FILE_TIMESTAMP.format(run.getStartedAt()) + "-" + run.getId() + ".zip";
            Path archive = backupDirectory.resolve(filename).toAbsolutePath().normalize();
            Path tempDir = Files.createTempDirectory("voyager-backup-");
            try {
                Path dump = tempDir.resolve(POSTGRES_ENTRY);
                runCommandToFile(properties.getBackup().getPgDumpCommand(), dump);

                List<StorageService.StoredObject> objects = storage.listObjects();
                List<MinioObjectManifest> objectManifests = new ArrayList<>();
                for (StorageService.StoredObject object : objects) {
                    objectManifests.add(new MinioObjectManifest(
                            object.objectKey(),
                            "minio/objects/" + encodedEntryName(object.objectKey()),
                            object.contentType(),
                            object.size()));
                }
                BackupManifest manifest = new BackupManifest(
                        "voyager-docs-backup-v1",
                        run.getId().toString(),
                        run.getTriggerType().name(),
                        DateTimeFormatter.ISO_INSTANT.format(run.getStartedAt()),
                        POSTGRES_ENTRY,
                        objectManifests);
                String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    putBytes(zip, MANIFEST_ENTRY, manifestJson.getBytes(StandardCharsets.UTF_8));
                    putFile(zip, POSTGRES_ENTRY, dump);
                    for (StorageService.StoredObject object : objects) {
                        ZipEntry entry = new ZipEntry("minio/objects/" + encodedEntryName(object.objectKey()));
                        zip.putNextEntry(entry);
                        try (InputStream input = storage.get(object.objectKey())) {
                            input.transferTo(zip);
                        }
                        zip.closeEntry();
                    }
                }
                return new BackupArtifact(
                        archive,
                        filename,
                        Files.size(archive),
                        sha256(archive),
                        objects.size(),
                        manifestJson);
            } finally {
                deleteTree(tempDir);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create backup archive", exception);
        }
    }

    private void restoreArchive(Path archive) {
        try {
            Path tempDir = Files.createTempDirectory("voyager-restore-");
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                BackupManifest manifest = readManifest(zip);
                Path dump = tempDir.resolve(POSTGRES_ENTRY);
                try (InputStream input = zip.getInputStream(requireEntry(zip, manifest.postgresEntry()))) {
                    Files.copy(input, dump, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                runCommandWithInput(properties.getBackup().getPgRestoreCommand(), dump);

                storage.clearBucket();
                for (MinioObjectManifest object : manifest.objects()) {
                    ZipEntry entry = requireEntry(zip, object.entryName());
                    Path objectFile = tempDir.resolve(encodedEntryName(object.objectKey()));
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, objectFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    storage.putFile(object.objectKey(), objectFile, object.contentType());
                    Files.deleteIfExists(objectFile);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to restore backup archive", exception);
            } finally {
                deleteTree(tempDir);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare restore workspace", exception);
        }
    }

    private void synchronizeBackupFiles() {
        Path directory = backupDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "voyager-full-backup-*.zip")) {
            for (Path archive : files) {
                try (ZipFile zip = new ZipFile(archive.toFile())) {
                    BackupManifest manifest = readManifest(zip);
                    UUID runId = UUID.fromString(manifest.runId());
                    BackupRun run = backupRuns.findById(runId).orElseGet(() -> {
                        BackupRun created = new BackupRun();
                        created.setId(runId);
                        return created;
                    });
                    if (run.getStatus() == BackupRunStatus.SUCCEEDED
                            && StringUtils.hasText(run.getBackupPath())
                            && Files.isRegularFile(Path.of(run.getBackupPath()))) {
                        continue;
                    }
                    run.setStatus(BackupRunStatus.SUCCEEDED);
                    run.setTriggerType(parseTriggerType(manifest.triggerType()));
                    run.setBackupFilename(archive.getFileName().toString());
                    run.setBackupPath(archive.toAbsolutePath().normalize().toString());
                    run.setFileSize(Files.size(archive));
                    run.setSha256(sha256(archive));
                    run.setObjectCount(manifest.objects().size());
                    run.setManifestJson(objectMapper.writeValueAsString(manifest));
                    Instant created = Instant.parse(manifest.createdAt());
                    run.setStartedAt(created);
                    run.setCompletedAt(created);
                    run.setCreatedAt(created);
                    backupRuns.save(run);
                } catch (RuntimeException | IOException ignored) {
                    // Ignore broken files; failed backups remain represented by backup_runs.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan backup directory", exception);
        }
    }

    private BackupSetting requireSettings() {
        return backupSettings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Backup settings are not initialized"));
    }

    private AppUser requireAdmin() {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin permission required");
        }
        return user;
    }

    private BackupDtos.BackupSettingsResponse toSettingsResponse(BackupSetting setting) {
        return new BackupDtos.BackupSettingsResponse(
                setting.getId(),
                setting.isEnabled(),
                setting.getDailyTime(),
                setting.getUpdatedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(setting.getUpdatedAt()));
    }

    private BackupDtos.BackupRunResponse toRunResponse(BackupRun run) {
        AppUser startedBy = run.getStartedBy();
        return new BackupDtos.BackupRunResponse(
                run.getId(),
                run.getStatus().name(),
                run.getTriggerType().name(),
                startedBy == null ? null : startedBy.getId(),
                startedBy == null ? null : startedBy.getDisplayName(),
                run.getBackupFilename(),
                run.getFileSize(),
                run.getSha256(),
                run.getObjectCount(),
                run.getErrorMessage(),
                DateTimeFormatter.ISO_INSTANT.format(run.getStartedAt()),
                run.getCompletedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(run.getCompletedAt()),
                DateTimeFormatter.ISO_INSTANT.format(run.getCreatedAt()));
    }

    private Path requireBackupArchive(BackupRun run) {
        if (!StringUtils.hasText(run.getBackupPath())) {
            throw new IllegalArgumentException("Backup archive path is missing");
        }
        Path archive = Path.of(run.getBackupPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("Backup archive file is missing");
        }
        return archive;
    }

    private Path backupDirectory() {
        return Path.of(properties.getBackup().getDirectory()).toAbsolutePath().normalize();
    }

    private BackupManifest readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = requireEntry(zip, MANIFEST_ENTRY);
        try (InputStream input = zip.getInputStream(entry)) {
            return objectMapper.readValue(input, BackupManifest.class);
        }
    }

    private ZipEntry requireEntry(ZipFile zip, String entryName) {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new IllegalArgumentException("Backup archive is missing " + entryName);
        }
        return entry;
    }

    private void runCommandToFile(String command, Path output) {
        runProcess(command, null, output);
    }

    private void runCommandWithInput(String command, Path input) {
        runProcess(command, input, null);
    }

    private void runProcess(String command, Path input, Path output) {
        try {
            Path error = Files.createTempFile("voyager-command-", ".err");
            try {
                ProcessBuilder builder = new ProcessBuilder(splitCommand(command));
                if (input != null) {
                    builder.redirectInput(input.toFile());
                }
                if (output != null) {
                    builder.redirectOutput(output.toFile());
                } else {
                    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                }
                builder.redirectError(error.toFile());
                Process process = builder.start();
                int code = process.waitFor();
                if (code != 0) {
                    String message = Files.readString(error, StandardCharsets.UTF_8);
                    throw new IllegalStateException("Command failed with exit code " + code + ": " + trim(message, 1000));
                }
            } finally {
                Files.deleteIfExists(error);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run command: " + command, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command interrupted: " + command, exception);
        }
    }

    private List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < command.length(); index++) {
            char ch = command.charAt(index);
            if ((ch == '"' || ch == '\'') && (!quoted || quote == ch)) {
                quoted = !quoted;
                quote = quoted ? ch : 0;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Backup command must not be empty");
        }
        return parts;
    }

    private void putBytes(ZipOutputStream zip, String entryName, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void putFile(ZipOutputStream zip, String entryName, Path source) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        Files.copy(source, zip);
        zip.closeEntry();
    }

    private String encodedEntryName(String objectKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectKey.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate backup checksum", exception);
        }
    }

    private BackupTriggerType parseTriggerType(String value) {
        try {
            return BackupTriggerType.valueOf(value);
        } catch (RuntimeException exception) {
            return BackupTriggerType.MANUAL;
        }
    }

    private void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Temporary cleanup best effort.
                }
            });
        } catch (IOException ignored) {
            // Temporary cleanup best effort.
        }
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private record BackupArtifact(
            Path path,
            String filename,
            long fileSize,
            String sha256,
            int objectCount,
            String manifestJson) {
    }

    private record BackupManifest(
            String formatVersion,
            String runId,
            String triggerType,
            String createdAt,
            String postgresEntry,
            List<MinioObjectManifest> objects) {
    }

    private record MinioObjectManifest(String objectKey, String entryName, String contentType, long size) {
    }
}
