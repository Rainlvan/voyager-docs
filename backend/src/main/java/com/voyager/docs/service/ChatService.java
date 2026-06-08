package com.voyager.docs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.ChatMessage;
import com.voyager.docs.domain.ChatRole;
import com.voyager.docs.domain.ChatSession;
import com.voyager.docs.dto.ChatDtos;
import com.voyager.docs.dto.SearchDtos;
import com.voyager.docs.repository.ChatMessageRepository;
import com.voyager.docs.repository.ChatSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatService {
    private static final String DEFAULT_TITLE = "New document chat";

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final CurrentUserService currentUserService;
    private final SearchService searchService;
    private final BailianChatService bailianChatService;
    private final ObjectMapper objectMapper;
    private final MaintenanceService maintenanceService;
    private final AuditService auditService;

    public ChatService(
            ChatSessionRepository sessions,
            ChatMessageRepository messages,
            CurrentUserService currentUserService,
            SearchService searchService,
            BailianChatService bailianChatService,
            ObjectMapper objectMapper,
            MaintenanceService maintenanceService,
            AuditService auditService) {
        this.sessions = sessions;
        this.messages = messages;
        this.currentUserService = currentUserService;
        this.searchService = searchService;
        this.bailianChatService = bailianChatService;
        this.objectMapper = objectMapper;
        this.maintenanceService = maintenanceService;
        this.auditService = auditService;
    }

    @Transactional
    public ChatDtos.ChatSessionResponse createSession(ChatDtos.CreateChatSessionRequest request) {
        maintenanceService.requireAvailableForWrites();
        AppUser user = currentUserService.requireCurrentUser();
        ChatSession session = new ChatSession();
        session.setCreatedBy(user);
        session.setTitle(StringUtils.hasText(request.title()) ? request.title().trim() : DEFAULT_TITLE);
        return toSessionResponse(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ChatSessionResponse> listSessions() {
        AppUser user = currentUserService.requireCurrentUser();
        return sessions.findTop30ByCreatedByIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ChatMessageResponse> listMessages(UUID sessionId) {
        requireSession(sessionId);
        return messages.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        maintenanceService.requireAvailableForWrites();
        ChatSession session = requireSession(sessionId);
        messages.deleteBySessionId(session.getId());
        sessions.delete(session);
        auditService.recordCurrent("CHAT_SESSION_DELETE", "CHAT_SESSION", session.getId().toString(), true,
                "Deleted chat session");
    }

    @Transactional
    public ChatDtos.ChatAnswerResponse send(UUID sessionId, ChatDtos.SendMessageRequest request) {
        maintenanceService.requireAvailableForWrites();
        ChatSession session = requireSession(sessionId);
        String question = request.content().trim();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setRole(ChatRole.USER);
        userMessage.setContent(question);
        messages.save(userMessage);

        SearchDtos.SearchResponse search = searchService.aiSearch(new SearchDtos.AiSearchRequest(question, 5));
        List<ChatDtos.Citation> citations = search.hits().stream()
                .map(hit -> new ChatDtos.Citation(hit.documentId(), hit.title(), hit.pageNumber(), hit.reason()))
                .toList();

        ChatMessage assistant = new ChatMessage();
        assistant.setSession(session);
        assistant.setRole(ChatRole.ASSISTANT);
        assistant.setContent(bailianChatService.answer(question, citations).orElseGet(() -> buildAnswer(search)));
        assistant.setCitationsJson(writeCitations(citations));
        messages.save(assistant);

        if (DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(question.substring(0, Math.min(30, question.length())));
        }
        return new ChatDtos.ChatAnswerResponse(toMessageResponse(userMessage), toMessageResponse(assistant));
    }

    private ChatSession requireSession(UUID sessionId) {
        AppUser user = currentUserService.requireCurrentUser();
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found"));
        if (!session.getCreatedBy().getId().equals(user.getId())) {
            throw new EntityNotFoundException("Chat session not found");
        }
        return session;
    }

    private String buildAnswer(SearchDtos.SearchResponse search) {
        if (search.hits().isEmpty()) {
            return "No indexed document matched this question yet. Try another keyword or wait for parsing to finish.";
        }
        String titles = search.hits().stream()
                .limit(3)
                .map(SearchDtos.SearchHit::title)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "Found " + search.hits().size() + " likely related document result(s). Start with: " + titles + ".";
    }

    private String writeCitations(List<ChatDtos.Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private ChatDtos.ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatDtos.ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                DateTimeFormatter.ISO_INSTANT.format(session.getCreatedAt()),
                DateTimeFormatter.ISO_INSTANT.format(session.getUpdatedAt()));
    }

    private ChatDtos.ChatMessageResponse toMessageResponse(ChatMessage message) {
        List<ChatDtos.Citation> citations = List.of();
        if (StringUtils.hasText(message.getCitationsJson())) {
            try {
                citations = objectMapper.readValue(message.getCitationsJson(), new TypeReference<>() {
                });
            } catch (JsonProcessingException ignored) {
                citations = List.of();
            }
        }
        return new ChatDtos.ChatMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                citations,
                DateTimeFormatter.ISO_INSTANT.format(message.getCreatedAt()));
    }
}
