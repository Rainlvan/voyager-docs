package com.voyager.docs.repository;

import com.voyager.docs.domain.IngestionJob;
import com.voyager.docs.domain.IngestionJobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    List<IngestionJob> findTop20ByStatusOrderByCreatedAtAsc(IngestionJobStatus status);

    List<IngestionJob> findTop20ByOrderByCreatedAtDesc();

    @Modifying
    @Query("delete from IngestionJob j where j.document.id = :documentId")
    void deleteByDocument_Id(@Param("documentId") UUID documentId);
}
