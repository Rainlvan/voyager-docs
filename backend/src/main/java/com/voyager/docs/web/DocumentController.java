package com.voyager.docs.web;

import com.voyager.docs.dto.DocumentDtos;
import com.voyager.docs.service.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentDtos.DocumentResponse> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    public DocumentDtos.DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        DocumentService.DownloadObject download = documentService.download(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (download.contentType() != null && !download.contentType().isBlank()) {
                mediaType = MediaType.parseMediaType(download.contentType());
            }
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(download.stream()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable UUID id) {
        DocumentService.DownloadObject preview = documentService.preview(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (preview.contentType() != null && !preview.contentType().isBlank()) {
                mediaType = MediaType.parseMediaType(preview.contentType());
            }
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(preview.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(preview.stream()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDtos.DocumentResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "folderId", required = false) UUID folderId) {
        return documentService.upload(file, title, folderId);
    }

    @PatchMapping("/{id}/folder")
    public DocumentDtos.DocumentResponse move(
            @PathVariable UUID id,
            @RequestBody DocumentDtos.MoveDocumentRequest request) {
        return documentService.move(id, request);
    }

    @PostMapping("/{id}/reindex")
    public DocumentDtos.ReindexResponse reindex(@PathVariable UUID id) {
        return documentService.reindex(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        documentService.softDelete(id);
    }
}
