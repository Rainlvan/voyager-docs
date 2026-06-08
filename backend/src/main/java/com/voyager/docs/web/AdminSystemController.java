package com.voyager.docs.web;

import com.voyager.docs.dto.BackupDtos;
import com.voyager.docs.service.MaintenanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {
    private final MaintenanceService maintenanceService;

    public AdminSystemController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/maintenance")
    public BackupDtos.MaintenanceResponse maintenance() {
        return maintenanceService.status();
    }
}
