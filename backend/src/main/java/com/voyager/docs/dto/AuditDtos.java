package com.voyager.docs.dto;

import java.util.List;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEventResponse(
            UUID id,
            Long actorUserId,
            String actorUsername,
            String actorRole,
            String ipAddress,
            String userAgent,
            String action,
            String resourceType,
            String resourceId,
            boolean success,
            String summary,
            String createdAt) {
    }

    public record AuditEventPageResponse(List<AuditEventResponse> items, long total, int page, int size) {
    }
}
