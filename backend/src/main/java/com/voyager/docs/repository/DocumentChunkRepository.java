package com.voyager.docs.repository;

import com.voyager.docs.domain.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findTop8ByContentContainingIgnoreCaseOrderByCreatedAtDesc(String query);

    @Modifying
    @Query("delete from DocumentChunk c where c.document.id = :documentId")
    void deleteByDocument_Id(@Param("documentId") UUID documentId);
}
