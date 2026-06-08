package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class FolderDtos {
    private FolderDtos() {
    }

    public record FolderResponse(
            UUID id,
            String name,
            UUID parentId,
            long documentCount) {
    }

    public record CreateFolderRequest(
            @NotBlank
            @Size(max = 120)
            String name,
            UUID parentId) {
    }

    public record UpdateFolderRequest(
            @NotBlank
            @Size(max = 120)
            String name) {
    }
}
