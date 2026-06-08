package com.voyager.docs.repository;

import com.voyager.docs.domain.WorkerHeartbeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, String> {
    List<WorkerHeartbeat> findTop20ByOrderByLastSeenAtDesc();
}
