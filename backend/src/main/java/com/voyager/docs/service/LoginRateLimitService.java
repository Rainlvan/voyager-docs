package com.voyager.docs.service;

import com.voyager.docs.config.AppProperties;
import com.voyager.docs.domain.LoginRateLimit;
import com.voyager.docs.repository.LoginRateLimitRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LoginRateLimitService {
    private final LoginRateLimitRepository limits;
    private final AppProperties properties;
    private final AuditService auditService;

    public LoginRateLimitService(LoginRateLimitRepository limits, AppProperties properties, AuditService auditService) {
        this.limits = limits;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public void assertAllowed(String username) {
        String normalized = normalize(username);
        String ip = auditService.currentIpAddress();
        limits.findByUsernameAndIpAddress(normalized, ip).ifPresent(limit -> {
            Instant lockedUntil = limit.getLockedUntil();
            if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
                throw new LoginRateLimitException("Too many login attempts. Please try again later.");
            }
        });
    }

    @Transactional
    public void recordFailure(String username) {
        String normalized = normalize(username);
        String ip = auditService.currentIpAddress();
        Instant now = Instant.now();
        LoginRateLimit limit = limits.findByUsernameAndIpAddress(normalized, ip).orElseGet(() -> {
            LoginRateLimit next = new LoginRateLimit();
            next.setUsername(normalized);
            next.setIpAddress(ip);
            return next;
        });
        Duration window = Duration.ofMinutes(properties.getSecurity().getLoginWindowMinutes());
        if (limit.getFirstFailedAt() == null || limit.getFirstFailedAt().isBefore(now.minus(window))) {
            limit.setFailureCount(0);
            limit.setFirstFailedAt(now);
            limit.setLockedUntil(null);
        }
        limit.setFailureCount(limit.getFailureCount() + 1);
        limit.setLastFailedAt(now);
        if (limit.getFailureCount() >= properties.getSecurity().getLoginMaxFailures()) {
            limit.setLockedUntil(now.plus(Duration.ofMinutes(properties.getSecurity().getLoginLockMinutes())));
        }
        limits.save(limit);
    }

    @Transactional
    public void clear(String username) {
        limits.deleteByUsernameAndIpAddress(normalize(username), auditService.currentIpAddress());
    }

    private String normalize(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase(Locale.ROOT) : "";
    }
}
