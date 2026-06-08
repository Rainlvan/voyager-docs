package com.voyager.docs.repository;

import com.voyager.docs.domain.DocumentFolder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, UUID> {
    List<DocumentFolder> findAllByOrderByNameAsc();

    boolean existsByParent_Id(UUID parentId);

    @Query("""
            select case when count(folder) > 0 then true else false end
            from DocumentFolder folder
            where lower(folder.name) = lower(:name)
              and (:excludedId is null or folder.id <> :excludedId)
              and (
                (:parentId is null and folder.parent is null)
                or (:parentId is not null and folder.parent.id = :parentId)
              )
            """)
    boolean existsSiblingName(
            @Param("parentId") UUID parentId,
            @Param("name") String name,
            @Param("excludedId") UUID excludedId);

    @Modifying
    @Query("delete from DocumentFolder folder where folder.id = :id")
    void deleteDirectById(@Param("id") UUID id);
}
