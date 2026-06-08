package com.voyager.docs.repository;

import com.voyager.docs.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    @Modifying
    @Query("delete from ChatMessage m where m.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
