package com.voyager.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.domain.AiSetting;
import com.voyager.docs.dto.ChatDtos;
import com.voyager.docs.repository.AiSettingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class BailianChatService {
    private final AiSettingRepository settings;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public BailianChatService(AiSettingRepository settings, CryptoService cryptoService, ObjectMapper objectMapper) {
        this.settings = settings;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    public Optional<String> answer(String question, List<ChatDtos.Citation> citations) {
        AiSetting setting = settings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI 设置未初始化"));
        if (!StringUtils.hasText(setting.getApiKeyCiphertext())) {
            return Optional.empty();
        }

        String apiKey = cryptoService.decrypt(setting.getApiKeyCiphertext());
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }

        try {
            String response = RestClient.builder().baseUrl(chatBaseUrl(setting)).build()
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", setting.getChatModel(),
                            "temperature", 0.2,
                            "messages", List.of(
                                    Map.of(
                                            "role", "system",
                                            "content", systemPrompt()),
                                    Map.of(
                                            "role", "user",
                                            "content", userPrompt(question, citations)))))
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(parseContent(response)).filter(StringUtils::hasText);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String chatBaseUrl(AiSetting setting) {
        return "cn-beijing".equals(setting.getRegion())
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    }

    private String systemPrompt() {
        return """
                你是企业文档检索助手。只能基于提供的引用材料回答。
                如果引用不足以回答问题，明确说明没有找到足够依据。
                回答要简洁，优先说明相关文档名、页码和原因。
                不要编造不存在的文件、页码、人员或结论。
                """;
    }

    private String userPrompt(String question, List<ChatDtos.Citation> citations) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：").append(question).append("\n\n");
        builder.append("检索到的引用材料：\n");
        if (citations.isEmpty()) {
            builder.append("无\n");
        }
        for (int index = 0; index < citations.size(); index++) {
            ChatDtos.Citation citation = citations.get(index);
            builder.append(index + 1)
                    .append(". 文档：")
                    .append(citation.title())
                    .append(citation.pageNumber() == null ? "" : "，第 " + citation.pageNumber() + " 页")
                    .append("\n片段：")
                    .append(safeSnippet(citation.snippet()))
                    .append("\n");
        }
        builder.append("\n请基于以上引用回答，并在回答中点名引用到的文档。");
        return builder.toString();
    }

    private String safeSnippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 1200 ? value.substring(0, 1200) : value;
    }

    private String parseContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("解析百炼 Chat 响应失败", exception);
        }
    }
}
