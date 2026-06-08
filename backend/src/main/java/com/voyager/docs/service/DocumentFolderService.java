package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.Document;
import com.voyager.docs.domain.DocumentFolder;
import com.voyager.docs.domain.DocumentStatus;
import com.voyager.docs.domain.UserRole;
import com.voyager.docs.dto.FolderDtos;
import com.voyager.docs.repository.DocumentFolderRepository;
import com.voyager.docs.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentFolderService {
    private final DocumentFolderRepository folders;
    private final DocumentRepository documents;
    private final CurrentUserService currentUserService;
    private final OpenSearchIndexService openSearchIndexService;
    private final AuditService auditService;

    public DocumentFolderService(
            DocumentFolderRepository folders,
            DocumentRepository documents,
            CurrentUserService currentUserService,
            OpenSearchIndexService openSearchIndexService,
            AuditService auditService) {
        this.folders = folders;
        this.documents = documents;
        this.currentUserService = currentUserService;
        this.openSearchIndexService = openSearchIndexService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FolderDtos.FolderResponse> list() {
        currentUserService.requireCurrentUser();
        return folders.findAllByOrderByNameAsc().stream()
                .sorted(Comparator.comparing(DocumentFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FolderDtos.FolderResponse create(FolderDtos.CreateFolderRequest request) {
        AppUser admin = requireAdmin();
        String name = normalizeName(request.name());
        UUID parentId = request.parentId();
        DocumentFolder parent = parentId == null ? null : requireFolder(parentId);
        ensureUniqueSiblingName(parentId, name, null);

        DocumentFolder folder = new DocumentFolder();
        folder.setName(name);
        folder.setParent(parent);
        folder.setCreatedBy(admin);
        DocumentFolder saved = folders.save(folder);
        auditService.recordForUser(admin, "FOLDER_CREATE", "DOCUMENT_FOLDER", saved.getId().toString(), true,
                "Created folder " + saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public FolderDtos.FolderResponse update(UUID id, FolderDtos.UpdateFolderRequest request) {
        AppUser admin = requireAdmin();
        DocumentFolder folder = requireFolder(id);
        String name = normalizeName(request.name());
        UUID parentId = folder.getParent() == null ? null : folder.getParent().getId();
        ensureUniqueSiblingName(parentId, name, folder.getId());
        folder.setName(name);
        auditService.recordForUser(admin, "FOLDER_UPDATE", "DOCUMENT_FOLDER", folder.getId().toString(), true,
                "Renamed folder to " + name);
        return toResponse(folder);
    }

    @Transactional
    public void delete(UUID id) {
        AppUser admin = requireAdmin();
        DocumentFolder folder = requireFolder(id);
        List<DocumentFolder> allFolders = folders.findAll();
        Set<UUID> subtreeIds = collectSubtreeIds(id, allFolders);
        List<Document> folderDocuments = documents.findByDeletedAtIsNullAndRecycledAtIsNullAndFolder_IdIn(subtreeIds);
        Instant now = Instant.now();
        for (Document document : folderDocuments) {
            document.setDeletedAt(now);
            document.setStatus(DocumentStatus.DELETED);
            document.setFolder(null);
            openSearchIndexService.deleteDocument(document.getId());
        }
        List<DocumentFolder> subtreeFolders = allFolders.stream()
                .filter(candidate -> subtreeIds.contains(candidate.getId()))
                .sorted(Comparator.comparingInt((DocumentFolder candidate) -> depth(candidate, allFolders)).reversed())
                .toList();
        for (DocumentFolder subtreeFolder : subtreeFolders) {
            folders.deleteDirectById(subtreeFolder.getId());
        }
        auditService.recordForUser(admin, "FOLDER_DELETE", "DOCUMENT_FOLDER", id.toString(), true,
                "Deleted folder " + folder.getName() + " with "
                        + subtreeFolders.size() + " folder(s) and "
                        + folderDocuments.size() + " document(s)");
    }

    @Transactional(readOnly = true)
    public DocumentFolder requireFolder(UUID id) {
        return folders.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Folder not found"));
    }

    private AppUser requireAdmin() {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin permission required");
        }
        return user;
    }

    private FolderDtos.FolderResponse toResponse(DocumentFolder folder) {
        return new FolderDtos.FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParent() == null ? null : folder.getParent().getId(),
                documents.countByDeletedAtIsNullAndRecycledAtIsNullAndFolder_Id(folder.getId()));
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Folder name is required");
        }
        String name = value.trim();
        if (name.length() > 120) {
            throw new IllegalArgumentException("Folder name must be 120 characters or fewer");
        }
        return name;
    }

    private void ensureUniqueSiblingName(UUID parentId, String name, UUID excludedId) {
        if (folders.existsSiblingName(parentId, name, excludedId)) {
            throw new IllegalArgumentException("A folder with this name already exists here");
        }
    }

    private Set<UUID> collectSubtreeIds(UUID rootId, List<DocumentFolder> allFolders) {
        Map<UUID, List<DocumentFolder>> childrenByParent = allFolders.stream()
                .filter(folder -> folder.getParent() != null)
                .collect(Collectors.groupingBy(folder -> folder.getParent().getId()));
        List<UUID> ids = new ArrayList<>();
        collectSubtreeIds(rootId, childrenByParent, ids);
        return Set.copyOf(ids);
    }

    private void collectSubtreeIds(
            UUID folderId,
            Map<UUID, List<DocumentFolder>> childrenByParent,
            List<UUID> ids) {
        ids.add(folderId);
        for (DocumentFolder child : childrenByParent.getOrDefault(folderId, List.of())) {
            collectSubtreeIds(child.getId(), childrenByParent, ids);
        }
    }

    private int depth(DocumentFolder folder, List<DocumentFolder> allFolders) {
        int depth = 0;
        DocumentFolder current = folder;
        while (current.getParent() != null) {
            depth += 1;
            UUID parentId = current.getParent().getId();
            current = allFolders.stream()
                    .filter(candidate -> candidate.getId().equals(parentId))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                return depth;
            }
        }
        return depth;
    }
}
