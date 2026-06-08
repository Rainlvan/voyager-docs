package com.voyager.docs.repository;

import com.voyager.docs.domain.AiSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {
}
