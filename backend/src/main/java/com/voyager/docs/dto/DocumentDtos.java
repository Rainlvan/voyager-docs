package com.voyager.docs.dto;

import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record DocumentResponse(
            UUID id,
            String title,
            String originalFilename,
            String contentType,
            long fileSize,
            String status,
            Long uploadedById,
            String uploadedBy,
            String uploadedByUsername,
            boolean uploadedByEnabled,
            boolean inRecycleBin,
            boolean canDelete,
            boolean canReindex,
            UUID folderId,
            String folderName,
            boolean previewable,
            String createdAt,
            String updatedAt) {
    }

    public record MoveDocumentRequest(UUID folderId) {
    }

    public record ReindexResponse(UUID documentId, UUID jobId, String status) {
    }

    public record JobResponse(UUID id, UUID documentId, String documentTitle, String status, int attemptCount, String errorMessage, String createdAt, String updatedAt) {
    }

    public record WorkerResponse(String workerId, String status, UUID currentJobId, String message, String startedAt, String lastSeenAt, boolean online) {
    }
}
