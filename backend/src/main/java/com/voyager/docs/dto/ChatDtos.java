package com.voyager.docs.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record CreateChatSessionRequest(String title) {
    }

    public record ChatSessionResponse(UUID id, String title, String createdAt, String updatedAt) {
    }

    public record SendMessageRequest(@NotBlank String content) {
    }

    public record Citation(UUID documentId, String title, Integer pageNumber, String snippet) {
    }

    public record ChatMessageResponse(UUID id, String role, String content, List<Citation> citations, String createdAt) {
    }

    public record ChatAnswerResponse(ChatMessageResponse userMessage, ChatMessageResponse assistantMessage) {
    }
}
