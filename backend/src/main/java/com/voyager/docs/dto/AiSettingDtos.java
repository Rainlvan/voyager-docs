package com.voyager.docs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class AiSettingDtos {
    private AiSettingDtos() {
    }

    public record AiSettingResponse(
            Long id,
            String provider,
            String region,
            boolean apiKeyConfigured,
            String maskedApiKey,
            String chatModel,
            String textEmbeddingModel,
            int textEmbeddingDimension,
            String multimodalEmbeddingModel,
            int multimodalEmbeddingDimension,
            String rerankModel,
            String multimodalRerankModel,
            String embeddingInvocationMode,
            String updatedAt) {
    }

    public record UpdateAiSettingRequest(
            String apiKey,
            @NotBlank String region,
            @NotBlank String chatModel,
            @NotBlank String textEmbeddingModel,
            @Min(1) int textEmbeddingDimension,
            @NotBlank String multimodalEmbeddingModel,
            @Min(1) int multimodalEmbeddingDimension,
            @NotBlank String rerankModel,
            @NotBlank String multimodalRerankModel,
            @NotBlank String embeddingInvocationMode) {
    }

    public record AiSettingTestResponse(boolean ok, String message) {
    }
}
