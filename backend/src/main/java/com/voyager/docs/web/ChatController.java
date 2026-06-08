package com.voyager.docs.web;

import com.voyager.docs.dto.ChatDtos;
import com.voyager.docs.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatDtos.ChatSessionResponse> listSessions() {
        return chatService.listSessions();
    }

    @PostMapping
    public ChatDtos.ChatSessionResponse createSession(@RequestBody ChatDtos.CreateChatSessionRequest request) {
        return chatService.createSession(request);
    }

    @GetMapping("/{id}/messages")
    public List<ChatDtos.ChatMessageResponse> listMessages(@PathVariable UUID id) {
        return chatService.listMessages(id);
    }

    @DeleteMapping("/{id}")
    public void deleteSession(@PathVariable UUID id) {
        chatService.deleteSession(id);
    }

    @PostMapping("/{id}/messages")
    public ChatDtos.ChatAnswerResponse sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        return chatService.send(id, request);
    }
}
