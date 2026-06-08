package com.voyager.docs.web;

import com.voyager.docs.dto.AuditDtos;
import com.voyager.docs.service.AuditService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public AuditDtos.AuditEventPageResponse list(
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return auditService.list(actor, action, success, from, to, page, size);
    }
}
