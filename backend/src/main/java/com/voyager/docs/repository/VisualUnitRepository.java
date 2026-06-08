package com.voyager.docs.repository;

import com.voyager.docs.domain.VisualUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisualUnitRepository extends JpaRepository<VisualUnit, UUID> {
    List<VisualUnit> findByDocument_Id(UUID documentId);

    @Modifying
    @Query("delete from VisualUnit v where v.document.id = :documentId")
    void deleteByDocument_Id(@Param("documentId") UUID documentId);
}
