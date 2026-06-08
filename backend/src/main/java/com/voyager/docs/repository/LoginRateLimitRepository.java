package com.voyager.docs.repository;

import com.voyager.docs.domain.LoginRateLimit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRateLimitRepository extends JpaRepository<LoginRateLimit, Long> {
    Optional<LoginRateLimit> findByUsernameAndIpAddress(String username, String ipAddress);

    void deleteByUsernameAndIpAddress(String username, String ipAddress);
}
