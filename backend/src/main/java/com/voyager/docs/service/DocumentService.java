package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.Document;
import com.voyager.docs.domain.DocumentFolder;
import com.voyager.docs.domain.DocumentStatus;
import com.voyager.docs.domain.IngestionJob;
import com.voyager.docs.domain.IngestionJobStatus;
import com.voyager.docs.domain.UserRole;
import com.voyager.docs.domain.VisualUnit;
import com.voyager.docs.domain.WorkerHeartbeat;
import com.voyager.docs.dto.DocumentDtos;
import com.voyager.docs.repository.DocumentChunkRepository;
import com.voyager.docs.repository.DocumentFolderRepository;
import com.voyager.docs.repository.DocumentRepository;
import com.voyager.docs.repository.IngestionJobRepository;
import com.voyager.docs.repository.VisualUnitRepository;
import com.voyager.docs.repository.WorkerHeartbeatRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final DocumentFolderRepository folders;
    private final IngestionJobRepository jobs;
    private final WorkerHeartbeatRepository workers;
    private final DocumentChunkRepository chunks;
    private final VisualUnitRepository visuals;
    private final StorageService storage;
    private final CurrentUserService currentUserService;
    private final OpenSearchIndexService openSearchIndexService;
    private final JdbcTemplate jdbcTemplate;
    private final MaintenanceService maintenanceService;
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documents,
            DocumentFolderRepository folders,
            IngestionJobRepository jobs,
            WorkerHeartbeatRepository workers,
            DocumentChunkRepository chunks,
            VisualUnitRepository visuals,
            StorageService storage,
            CurrentUserService currentUserService,
            OpenSearchIndexService openSearchIndexService,
            JdbcTemplate jdbcTemplate,
            MaintenanceService maintenanceService,
            AuditService auditService) {
        this.documents = documents;
        this.folders = folders;
        this.jobs = jobs;
        this.workers = workers;
        this.chunks = chunks;
        this.visuals = visuals;
        this.storage = storage;
        this.currentUserService = currentUserService;
        this.openSearchIndexService = openSearchIndexService;
        this.jdbcTemplate = jdbcTemplate;
        this.maintenanceService = maintenanceService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> list() {
        AppUser user = currentUserService.requireCurrentUser();
        return documents.findTop100ByDeletedAtIsNullAndRecycledAtIsNullOrderByCreatedAtDesc().stream()
                .map(document -> toResponse(document, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> recycleBin() {
        AppUser admin = requireAdmin();
        return documents.findTop100ByDeletedAtIsNullAndRecycledAtIsNotNullOrderByUpdatedAtDesc().stream()
                .map(document -> toResponse(document, admin))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DocumentResponse get(UUID id) {
        AppUser user = currentUserService.requireCurrentUser();
        return toResponse(requireVisibleDocument(id), user);
    }

    @Transactional(readOnly = true)
    public DownloadObject download(UUID id) {
        AppUser user = currentUserService.requireCurrentUser();
        Document document = requireVisibleDocument(id);
        auditService.recordForUser(user, "DOCUMENT_DOWNLOAD", "DOCUMENT", document.getId().toString(), true,
                "Downloaded " + document.getOriginalFilename());
        return new DownloadObject(
                storage.get(document.getObjectKey()),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize());
    }

    @Transactional(readOnly = true)
    public DownloadObject preview(UUID id) {
        currentUserService.requireCurrentUser();
        Document document = requireVisibleDocument(id);
        if (!isPreviewableImage(document.getContentType())) {
            throw new IllegalArgumentException("Document is not previewable");
        }
        return new DownloadObject(
                storage.get(document.getObjectKey()),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize());
    }

    @Transactional
    public DocumentDtos.DocumentResponse upload(MultipartFile file, String title, UUID folderId) {
        maintenanceService.requireAvailableForWrites();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        AppUser user = currentUserService.requireCurrentUser();
        DocumentFolder folder = folderId == null ? null : requireFolder(folderId);
        String original = sanitize(file.getOriginalFilename());
        String documentTitle = StringUtils.hasText(title) ? title.trim() : stripExtension(original);
        if (!StringUtils.hasText(documentTitle)) {
            documentTitle = stripExtension(original);
        }
        if (documentTitle.length() > 500) {
            throw new IllegalArgumentException("Document title must be 500 characters or fewer");
        }
        UUID id = UUID.randomUUID();
        String objectKey = "documents/" + id + "/" + original;

        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload document", exception);
        }

        Document document = new Document();
        document.setId(id);
        document.setTitle(documentTitle);
        document.setOriginalFilename(original);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setObjectKey(objectKey);
        document.setStatus(DocumentStatus.PENDING);
        document.setUploadedBy(user);
        document.setFolder(folder);
        Document saved = documents.save(document);
        createJob(saved);
        auditService.recordForUser(user, "DOCUMENT_UPLOAD", "DOCUMENT", saved.getId().toString(), true,
                "Uploaded " + saved.getOriginalFilename());
        return toResponse(saved, user);
    }

    @Transactional
    public DocumentDtos.DocumentResponse move(UUID id, DocumentDtos.MoveDocumentRequest request) {
        maintenanceService.requireAvailableForWrites();
        AppUser user = currentUserService.requireCurrentUser();
        Document document = requireVisibleDocument(id);
        requireDocumentManager(document);
        DocumentFolder folder = request.folderId() == null ? null : requireFolder(request.folderId());
        document.setFolder(folder);
        auditService.recordForUser(user, "DOCUMENT_MOVE", "DOCUMENT", document.getId().toString(), true,
                folder == null ? "Moved document to unfiled" : "Moved document to folder " + folder.getName());
        return toResponse(document, user);
    }

    @Transactional
    public DocumentDtos.ReindexResponse reindex(UUID id) {
        maintenanceService.requireAvailableForWrites();
        Document document = requireVisibleDocument(id);
        requireDocumentManager(document);
        document.setStatus(DocumentStatus.PENDING);
        IngestionJob job = createJob(document);
        auditService.recordCurrent("DOCUMENT_REINDEX", "DOCUMENT", document.getId().toString(), true,
                "Queued document reindex");
        return new DocumentDtos.ReindexResponse(document.getId(), job.getId(), job.getStatus().name());
    }

    @Transactional
    public void softDelete(UUID id) {
        maintenanceService.requireAvailableForWrites();
        Document document = requireVisibleDocument(id);
        requireDocumentManager(document);
        document.setDeletedAt(Instant.now());
        document.setStatus(DocumentStatus.DELETED);
        openSearchIndexService.deleteDocument(document.getId());
        auditService.recordCurrent("DOCUMENT_DELETE", "DOCUMENT", document.getId().toString(), true,
                "Soft deleted document");
    }

    @Transactional
    public DocumentDtos.ReindexResponse restoreFromRecycleBin(UUID id) {
        maintenanceService.requireAvailableForWrites();
        requireAdmin();
        Document document = requireRecycleDocument(id);
        document.setRecycledAt(null);
        document.setRecycledBy(null);
        document.setStatus(DocumentStatus.PENDING);
        IngestionJob job = createJob(document);
        auditService.recordCurrent("RECYCLE_RESTORE", "DOCUMENT", document.getId().toString(), true,
                "Restored document from recycle bin");
        return new DocumentDtos.ReindexResponse(document.getId(), job.getId(), job.getStatus().name());
    }

    @Transactional
    public void permanentlyDelete(UUID id) {
        maintenanceService.requireAvailableForWrites();
        requireAdmin();
        Document document = requireRecycleDocument(id);
        openSearchIndexService.deleteDocument(document.getId());
        removeObjectQuietly(document.getObjectKey());
        for (VisualUnit visual : visuals.findByDocument_Id(document.getId())) {
            if (StringUtils.hasText(visual.getObjectKey())) {
                removeObjectQuietly(visual.getObjectKey());
            }
        }
        jdbcTemplate.update("delete from embedding_batches where document_id = ?", document.getId());
        chunks.deleteByDocument_Id(document.getId());
        visuals.deleteByDocument_Id(document.getId());
        jobs.deleteByDocument_Id(document.getId());
        documents.hardDeleteById(document.getId());
        auditService.recordCurrent("RECYCLE_PERMANENT_DELETE", "DOCUMENT", id.toString(), true,
                "Permanently deleted recycled document");
    }

    @Transactional
    public void recycleDocumentsByUser(AppUser owner, AppUser admin) {
        Instant now = Instant.now();
        for (Document document : documents.findByDeletedAtIsNullAndRecycledAtIsNullAndUploadedBy(owner)) {
            document.setRecycledAt(now);
            document.setRecycledBy(admin);
            document.setStatus(DocumentStatus.DELETED);
            openSearchIndexService.deleteDocument(document.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.JobResponse> recentJobs() {
        return jobs.findTop20ByOrderByCreatedAtDesc().stream()
                .filter(job -> job.getDocument().getDeletedAt() == null && job.getDocument().getRecycledAt() == null)
                .map(this::toJobResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.WorkerResponse> recentWorkers() {
        return workers.findTop20ByOrderByLastSeenAtDesc().stream().map(this::toWorkerResponse).toList();
    }

    @Transactional
    public DocumentDtos.JobResponse retryJob(UUID jobId) {
        maintenanceService.requireAvailableForWrites();
        IngestionJob job = jobs.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Ingestion job not found"));
        if (job.getDocument().getDeletedAt() != null || job.getDocument().getRecycledAt() != null) {
            throw new EntityNotFoundException("Document not found");
        }
        job.setStatus(IngestionJobStatus.PENDING);
        job.setErrorMessage(null);
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.getDocument().setStatus(DocumentStatus.PENDING);
        auditService.recordCurrent("JOB_RETRY", "INGESTION_JOB", job.getId().toString(), true,
                "Retried ingestion job");
        return toJobResponse(job);
    }

    @Transactional
    public void enqueueAllVisibleForReindexAfterRestore() {
        openSearchIndexService.deleteIndex();
        for (Document document : documents.findByDeletedAtIsNullAndRecycledAtIsNullOrderByCreatedAtAsc()) {
            for (VisualUnit visual : visuals.findByDocument_Id(document.getId())) {
                if (StringUtils.hasText(visual.getObjectKey())) {
                    removeObjectQuietly(visual.getObjectKey());
                }
            }
            jdbcTemplate.update("delete from embedding_batches where document_id = ?", document.getId());
            chunks.deleteByDocument_Id(document.getId());
            visuals.deleteByDocument_Id(document.getId());
            jobs.deleteByDocument_Id(document.getId());
            document.setStatus(DocumentStatus.PENDING);
            createJob(document);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> titleSearch(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        AppUser user = currentUserService.requireCurrentUser();
        return documents.findTop50ByDeletedAtIsNullAndRecycledAtIsNullAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(query.trim()).stream()
                .map(document -> toResponse(document, user))
                .toList();
    }

    public Document requireVisibleDocument(UUID id) {
        Document document = documents.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        if (document.getDeletedAt() != null || document.getRecycledAt() != null) {
            throw new EntityNotFoundException("Document not found");
        }
        return document;
    }

    public Document requireRecycleDocument(UUID id) {
        Document document = documents.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recycle-bin document not found"));
        if (document.getDeletedAt() != null || document.getRecycledAt() == null) {
            throw new EntityNotFoundException("Recycle-bin document not found");
        }
        return document;
    }

    private DocumentFolder requireFolder(UUID id) {
        return folders.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Folder not found"));
    }

    private IngestionJob createJob(Document document) {
        IngestionJob job = new IngestionJob();
        job.setDocument(document);
        job.setStatus(IngestionJobStatus.PENDING);
        return jobs.save(job);
    }

    private void requireDocumentManager(Document document) {
        AppUser user = currentUserService.requireCurrentUser();
        if (!canManageDocument(document, user)) {
            throw new AccessDeniedException("Only the uploader or an admin can manage this document");
        }
    }

    private AppUser requireAdmin() {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin permission required");
        }
        return user;
    }

    private boolean canManageDocument(Document document, AppUser user) {
        return user.getRole() == UserRole.ADMIN || document.getUploadedBy().getId().equals(user.getId());
    }

    private DocumentDtos.DocumentResponse toResponse(Document document, AppUser user) {
        boolean canManage = canManageDocument(document, user);
        AppUser uploader = document.getUploadedBy();
        boolean uploaderEnabled = uploader.isEnabled() && uploader.getDeletedAt() == null;
        DocumentFolder folder = document.getFolder();
        return new DocumentDtos.DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus().name(),
                uploader.getId(),
                displayUploader(uploader),
                uploader.getUsername(),
                uploaderEnabled,
                document.getRecycledAt() != null,
                canManage,
                canManage,
                folder == null ? null : folder.getId(),
                folder == null ? null : folder.getName(),
                isPreviewableImage(document.getContentType()),
                DateTimeFormatter.ISO_INSTANT.format(document.getCreatedAt()),
                DateTimeFormatter.ISO_INSTANT.format(document.getUpdatedAt()));
    }

    private String displayUploader(AppUser user) {
        if (user.getDeletedAt() != null) {
            return user.getDisplayName() + " (deleted)";
        }
        if (!user.isEnabled()) {
            return user.getDisplayName() + " (disabled)";
        }
        return user.getDisplayName();
    }

    private DocumentDtos.JobResponse toJobResponse(IngestionJob job) {
        return new DocumentDtos.JobResponse(
                job.getId(),
                job.getDocument().getId(),
                job.getDocument().getTitle(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.getErrorMessage(),
                DateTimeFormatter.ISO_INSTANT.format(job.getCreatedAt()),
                DateTimeFormatter.ISO_INSTANT.format(job.getUpdatedAt()));
    }

    private DocumentDtos.WorkerResponse toWorkerResponse(WorkerHeartbeat worker) {
        boolean online = worker.getLastSeenAt() != null
                && worker.getLastSeenAt().isAfter(Instant.now().minus(Duration.ofSeconds(45)));
        return new DocumentDtos.WorkerResponse(
                worker.getWorkerId(),
                worker.getStatus(),
                worker.getCurrentJobId(),
                worker.getMessage(),
                DateTimeFormatter.ISO_INSTANT.format(worker.getStartedAt()),
                DateTimeFormatter.ISO_INSTANT.format(worker.getLastSeenAt()),
                online);
    }

    private void removeObjectQuietly(String objectKey) {
        try {
            storage.remove(objectKey);
        } catch (RuntimeException ignored) {
            // Object cleanup should not block database cleanup during permanent deletion.
        }
    }

    private String sanitize(String filename) {
        String fallback = "document";
        if (!StringUtils.hasText(filename)) {
            return fallback;
        }
        return Paths.get(filename).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private boolean isPreviewableImage(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("image/png")
                || normalized.equals("image/jpeg")
                || normalized.equals("image/jpg")
                || normalized.equals("image/webp")
                || normalized.equals("image/gif");
    }

    public record DownloadObject(InputStream stream, String filename, String contentType, long contentLength) {
    }
}
