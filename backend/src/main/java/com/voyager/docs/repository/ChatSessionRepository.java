package com.voyager.docs.repository;

import com.voyager.docs.domain.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findTop30ByCreatedByIdOrderByUpdatedAtDesc(Long userId);
}
