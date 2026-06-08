package com.voyager.docs.web;

import com.voyager.docs.dto.DocumentDtos;
import com.voyager.docs.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class IngestionJobController {
    private final DocumentService documentService;

    public IngestionJobController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentDtos.JobResponse> recentJobs() {
        return documentService.recentJobs();
    }

    @GetMapping("/workers")
    public List<DocumentDtos.WorkerResponse> recentWorkers() {
        return documentService.recentWorkers();
    }

    @PostMapping("/{id}/retry")
    public DocumentDtos.JobResponse retry(@PathVariable UUID id) {
        return documentService.retryJob(id);
    }
}
