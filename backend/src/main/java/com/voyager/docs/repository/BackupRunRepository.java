package com.voyager.docs.repository;

import com.voyager.docs.domain.BackupRun;
import com.voyager.docs.domain.BackupRunStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRunRepository extends JpaRepository<BackupRun, UUID> {
    @EntityGraph(attributePaths = "startedBy")
    List<BackupRun> findAllByOrderByCreatedAtDesc();

    boolean existsByStatus(BackupRunStatus status);

    boolean existsByTriggerTypeAndCreatedAtBetween(com.voyager.docs.domain.BackupTriggerType triggerType, Instant from, Instant to);

    @EntityGraph(attributePaths = "startedBy")
    List<BackupRun> findByStatusIn(Collection<BackupRunStatus> statuses);

    @Override
    @EntityGraph(attributePaths = "startedBy")
    Optional<BackupRun> findById(UUID id);
}
