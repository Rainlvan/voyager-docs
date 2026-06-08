package com.voyager.docs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "system_maintenance")
public class SystemMaintenance {
    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String reason;

    @Column(name = "started_at")
    private Instant startedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by")
    private AppUser startedBy;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public AppUser getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(AppUser startedBy) {
        this.startedBy = startedBy;
    }
}
