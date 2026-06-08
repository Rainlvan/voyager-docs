package com.voyager.docs.web;

import com.voyager.docs.dto.DocumentDtos;
import com.voyager.docs.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recycle-bin/documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecycleBinController {
    private final DocumentService documentService;

    public AdminRecycleBinController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentDtos.DocumentResponse> list() {
        return documentService.recycleBin();
    }

    @PostMapping("/{id}/restore")
    public DocumentDtos.ReindexResponse restore(@PathVariable UUID id) {
        return documentService.restoreFromRecycleBin(id);
    }

    @DeleteMapping("/{id}")
    public void permanentlyDelete(@PathVariable UUID id) {
        documentService.permanentlyDelete(id);
    }
}
