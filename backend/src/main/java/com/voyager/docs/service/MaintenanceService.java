package com.voyager.docs.service;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.SystemMaintenance;
import com.voyager.docs.dto.BackupDtos;
import com.voyager.docs.repository.SystemMaintenanceRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceService {
    private static final short SINGLETON_ID = 1;

    private final SystemMaintenanceRepository maintenanceRepository;

    public MaintenanceService(SystemMaintenanceRepository maintenanceRepository) {
        this.maintenanceRepository = maintenanceRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return state().isEnabled();
    }

    @Transactional(readOnly = true)
    public BackupDtos.MaintenanceResponse status() {
        return toResponse(state());
    }

    @Transactional
    public void enter(String reason, AppUser startedBy) {
        SystemMaintenance state = state();
        state.setEnabled(true);
        state.setReason(reason);
        state.setStartedAt(Instant.now());
        state.setStartedBy(startedBy);
        maintenanceRepository.save(state);
    }

    @Transactional
    public void exit() {
        SystemMaintenance state = state();
        state.setEnabled(false);
        state.setReason(null);
        state.setStartedAt(null);
        state.setStartedBy(null);
        maintenanceRepository.save(state);
    }

    @Transactional(readOnly = true)
    public void requireAvailableForWrites() {
        if (state().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "System maintenance is in progress. Please try again later.");
        }
    }

    private SystemMaintenance state() {
        return maintenanceRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("System maintenance state is not initialized"));
    }

    private BackupDtos.MaintenanceResponse toResponse(SystemMaintenance state) {
        AppUser startedBy = state.getStartedBy();
        return new BackupDtos.MaintenanceResponse(
                state.isEnabled(),
                state.getReason(),
                state.getStartedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(state.getStartedAt()),
                startedBy == null ? null : startedBy.getId(),
                startedBy == null ? null : startedBy.getDisplayName());
    }
}
