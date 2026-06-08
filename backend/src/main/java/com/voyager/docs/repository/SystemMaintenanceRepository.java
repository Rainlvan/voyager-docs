package com.voyager.docs.repository;

import com.voyager.docs.domain.SystemMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemMaintenanceRepository extends JpaRepository<SystemMaintenance, Short> {
}
