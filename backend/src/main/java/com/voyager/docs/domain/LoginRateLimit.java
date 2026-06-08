package com.voyager.docs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "login_rate_limits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"username", "ip_address"}))
public class LoginRateLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "ip_address", nullable = false, length = 100)
    private String ipAddress;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "first_failed_at")
    private Instant firstFailedAt;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Instant getFirstFailedAt() {
        return firstFailedAt;
    }

    public void setFirstFailedAt(Instant firstFailedAt) {
        this.firstFailedAt = firstFailedAt;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Instant lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
