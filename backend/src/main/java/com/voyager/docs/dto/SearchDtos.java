package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {
    private SearchDtos() {
    }

    public record AiSearchRequest(@NotBlank String query, Integer limit) {
    }

    public record SearchHit(
            UUID documentId,
            String title,
            String originalFilename,
            String status,
            String reason,
            Integer pageNumber,
            double score) {
    }

    public record SearchResponse(String query, List<SearchHit> hits, String mode) {
    }
}
