package com.voyager.docs.repository;

import com.voyager.docs.domain.Document;
import com.voyager.docs.domain.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findTop100ByDeletedAtIsNullAndRecycledAtIsNullOrderByCreatedAtDesc();

    List<Document> findTop100ByDeletedAtIsNullAndRecycledAtIsNotNullOrderByUpdatedAtDesc();

    List<Document> findTop50ByDeletedAtIsNullAndRecycledAtIsNullAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(String title);

    List<Document> findByDeletedAtIsNullAndRecycledAtIsNullAndUploadedBy(AppUser uploadedBy);

    List<Document> findByDeletedAtIsNullAndRecycledAtIsNullOrderByCreatedAtAsc();

    List<Document> findByDeletedAtIsNullAndRecycledAtIsNullAndFolder_IdIn(Collection<UUID> folderIds);

    boolean existsByDeletedAtIsNullAndRecycledAtIsNullAndFolder_Id(UUID folderId);

    long countByDeletedAtIsNullAndRecycledAtIsNullAndFolder_Id(UUID folderId);

    @Modifying
    @Query("delete from Document d where d.id = :id")
    void hardDeleteById(@Param("id") UUID id);
}
