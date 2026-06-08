package com.voyager.docs.repository;

import com.voyager.docs.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
    long countByCreatedAtAfter(Instant createdAt);

    Page<AuditEvent> findAll(Pageable pageable);
}
