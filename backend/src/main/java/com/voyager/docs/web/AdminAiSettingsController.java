package com.voyager.docs.web;

import com.voyager.docs.dto.AiSettingDtos;
import com.voyager.docs.service.AiSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiSettingsController {
    private final AiSettingsService settingsService;

    public AdminAiSettingsController(AiSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public AiSettingDtos.AiSettingResponse get() {
        return settingsService.get();
    }

    @PutMapping
    public AiSettingDtos.AiSettingResponse update(@Valid @RequestBody AiSettingDtos.UpdateAiSettingRequest request) {
        return settingsService.update(request);
    }

    @PostMapping("/test")
    public AiSettingDtos.AiSettingTestResponse test() {
        return settingsService.test();
    }
}
