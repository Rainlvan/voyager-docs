package com.voyager.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.domain.AiSetting;
import com.voyager.docs.repository.AiSettingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class BailianEmbeddingService {
    private final AiSettingRepository settings;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public BailianEmbeddingService(AiSettingRepository settings, CryptoService cryptoService, ObjectMapper objectMapper) {
        this.settings = settings;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    public Optional<List<Double>> queryTextEmbedding(String query) {
        AiSetting setting = setting();
        String apiKey = apiKey(setting);
        if (!StringUtils.hasText(apiKey)) {
            return Optional.of(deterministicVector(query, setting.getTextEmbeddingDimension()));
        }
        try {
            String response = RestClient.builder().baseUrl(baseUrl(setting)).build()
                    .post()
                    .uri("/services/embeddings/text-embedding/text-embedding")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", setting.getTextEmbeddingModel(),
                            "input", Map.of("texts", List.of(query)),
                            "parameters", Map.of(
                                    "dimension", setting.getTextEmbeddingDimension(),
                                    "text_type", "query",
                                    "instruct", "Given an enterprise document search query, retrieve relevant document passages.")))
                    .retrieve()
                    .body(String.class);
            return Optional.of(parseEmbedding(response));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public Optional<List<Double>> queryVisualEmbedding(String query) {
        AiSetting setting = setting();
        String apiKey = apiKey(setting);
        if (!StringUtils.hasText(apiKey)) {
            return Optional.of(deterministicVector(query, setting.getMultimodalEmbeddingDimension()));
        }
        try {
            String response = RestClient.builder().baseUrl(baseUrl(setting)).build()
                    .post()
                    .uri("/services/embeddings/multimodal-embedding/multimodal-embedding")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", setting.getMultimodalEmbeddingModel(),
                            "input", Map.of("contents", List.of(Map.of("text", query))),
                            "parameters", Map.of(
                                    "dimension", setting.getMultimodalEmbeddingDimension(),
                                    "enable_fusion", true)))
                    .retrieve()
                    .body(String.class);
            return Optional.of(parseEmbedding(response));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private AiSetting setting() {
        return settings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI 设置未初始化"));
    }

    private String apiKey(AiSetting setting) {
        if (!StringUtils.hasText(setting.getApiKeyCiphertext())) {
            return "";
        }
        return cryptoService.decrypt(setting.getApiKeyCiphertext());
    }

    private String baseUrl(AiSetting setting) {
        return "cn-beijing".equals(setting.getRegion())
                ? "https://dashscope.aliyuncs.com/api/v1"
                : "https://dashscope-intl.aliyuncs.com/api/v1";
    }

    private List<Double> parseEmbedding(String response) {
        try {
            JsonNode node = objectMapper.readTree(response)
                    .path("output")
                    .path("embeddings")
                    .path(0)
                    .path("embedding");
            List<Double> values = new ArrayList<>();
            node.forEach(value -> values.add(value.asDouble()));
            return values;
        } catch (Exception exception) {
            throw new IllegalStateException("解析百炼向量响应失败", exception);
        }
    }

    private List<Double> deterministicVector(String seed, int dimension) {
        try {
            List<Double> values = new ArrayList<>(dimension);
            int counter = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            while (values.size() < dimension) {
                byte[] hash = digest.digest((seed + ":" + counter).getBytes(StandardCharsets.UTF_8));
                for (byte value : hash) {
                    values.add((((value & 0xff) / 255.0) * 2.0) - 1.0);
                    if (values.size() == dimension) {
                        break;
                    }
                }
                counter++;
            }
            double norm = Math.sqrt(values.stream().mapToDouble(value -> value * value).sum());
            if (norm == 0) {
                return values;
            }
            return values.stream().map(value -> value / norm).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("生成本地向量失败", exception);
        }
    }
}
