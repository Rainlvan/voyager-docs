package com.voyager.docs.service;

import com.voyager.docs.domain.AiSetting;
import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.EmbeddingInvocationMode;
import com.voyager.docs.dto.AiSettingDtos;
import com.voyager.docs.repository.AiSettingRepository;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiSettingsService {
    private final AiSettingRepository settings;
    private final CryptoService cryptoService;
    private final CurrentUserService currentUserService;
    private final MaintenanceService maintenanceService;
    private final AuditService auditService;

    public AiSettingsService(
            AiSettingRepository settings,
            CryptoService cryptoService,
            CurrentUserService currentUserService,
            MaintenanceService maintenanceService,
            AuditService auditService) {
        this.settings = settings;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
        this.maintenanceService = maintenanceService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AiSettingDtos.AiSettingResponse get() {
        return toResponse(requireSettings());
    }

    @Transactional
    public AiSettingDtos.AiSettingResponse update(AiSettingDtos.UpdateAiSettingRequest request) {
        maintenanceService.requireAvailableForWrites();
        AiSetting setting = requireSettings();
        AppUser user = currentUserService.requireCurrentUser();
        setting.setRegion(request.region().trim());
        setting.setChatModel(request.chatModel().trim());
        setting.setTextEmbeddingModel(request.textEmbeddingModel().trim());
        setting.setTextEmbeddingDimension(request.textEmbeddingDimension());
        setting.setMultimodalEmbeddingModel(request.multimodalEmbeddingModel().trim());
        setting.setMultimodalEmbeddingDimension(request.multimodalEmbeddingDimension());
        setting.setRerankModel(request.rerankModel().trim());
        setting.setMultimodalRerankModel(request.multimodalRerankModel().trim());
        setting.setEmbeddingInvocationMode(parseInvocationMode(request.embeddingInvocationMode()));
        setting.setUpdatedBy(user);
        if (StringUtils.hasText(request.apiKey())) {
            setting.setApiKeyCiphertext(cryptoService.encrypt(request.apiKey().trim()));
        }
        AiSetting saved = settings.save(setting);
        auditService.recordForUser(user, "AI_SETTINGS_UPDATE", "AI_SETTINGS", saved.getId().toString(), true,
                "Updated AI provider and model settings");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AiSettingDtos.AiSettingTestResponse test() {
        AiSetting setting = requireSettings();
        if (!StringUtils.hasText(setting.getApiKeyCiphertext())) {
            auditService.recordCurrent("AI_SETTINGS_TEST", "AI_SETTINGS", setting.getId().toString(), false,
                    "API key is not configured");
            return new AiSettingDtos.AiSettingTestResponse(false, "API key is not configured");
        }
        String key = cryptoService.decrypt(setting.getApiKeyCiphertext());
        boolean looksValid = key.startsWith("sk-") || key.length() >= 20;
        if (!looksValid) {
            auditService.recordCurrent("AI_SETTINGS_TEST", "AI_SETTINGS", setting.getId().toString(), false,
                    "API key format looks invalid");
            return new AiSettingDtos.AiSettingTestResponse(false, "API key format looks invalid");
        }
        auditService.recordCurrent("AI_SETTINGS_TEST", "AI_SETTINGS", setting.getId().toString(), true,
                "Local AI setting check passed");
        return new AiSettingDtos.AiSettingTestResponse(true, "Local AI setting check passed");
    }

    public AiSetting requireSettings() {
        return settings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI settings are not initialized"));
    }

    private EmbeddingInvocationMode parseInvocationMode(String value) {
        try {
            return EmbeddingInvocationMode.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("embeddingInvocationMode must be REALTIME or BATCH");
        }
    }

    private AiSettingDtos.AiSettingResponse toResponse(AiSetting setting) {
        boolean configured = StringUtils.hasText(setting.getApiKeyCiphertext());
        return new AiSettingDtos.AiSettingResponse(
                setting.getId(),
                setting.getProvider(),
                setting.getRegion(),
                configured,
                configured ? "sk-****" : "",
                setting.getChatModel(),
                setting.getTextEmbeddingModel(),
                setting.getTextEmbeddingDimension(),
                setting.getMultimodalEmbeddingModel(),
                setting.getMultimodalEmbeddingDimension(),
                setting.getRerankModel(),
                setting.getMultimodalRerankModel(),
                setting.getEmbeddingInvocationMode().name(),
                DateTimeFormatter.ISO_INSTANT.format(setting.getUpdatedAt()));
    }
}
