package com.voyager.docs.repository;

import com.voyager.docs.domain.BackupSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupSettingRepository extends JpaRepository<BackupSetting, Long> {
}
