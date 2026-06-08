package com.voyager.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.domain.AiSetting;
import com.voyager.docs.dto.SearchDtos;
import com.voyager.docs.repository.AiSettingRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class BailianRerankService {
    private final AiSettingRepository settings;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public BailianRerankService(AiSettingRepository settings, CryptoService cryptoService, ObjectMapper objectMapper) {
        this.settings = settings;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    public List<SearchDtos.SearchHit> rerank(String query, List<SearchDtos.SearchHit> hits, int topN) {
        if (hits.size() <= 1) {
            return hits;
        }
        AiSetting setting = settings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI 设置未初始化"));
        if (!StringUtils.hasText(setting.getApiKeyCiphertext())) {
            return hits;
        }

        String apiKey = cryptoService.decrypt(setting.getApiKeyCiphertext());
        if (!StringUtils.hasText(apiKey)) {
            return hits;
        }

        try {
            List<String> documents = hits.stream().map(this::rerankDocument).toList();
            String response = RestClient.builder().baseUrl(rerankBaseUrl(setting)).build()
                    .post()
                    .uri("/services/rerank/text-rerank/text-rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(rerankBody(setting.getRerankModel(), query, documents, topN))
                    .retrieve()
                    .body(String.class);
            return applyOrder(hits, response, topN);
        } catch (RuntimeException exception) {
            return hits;
        }
    }

    private String rerankBaseUrl(AiSetting setting) {
        return "cn-beijing".equals(setting.getRegion())
                ? "https://dashscope.aliyuncs.com/api/v1"
                : "https://dashscope-intl.aliyuncs.com/api/v1";
    }

    private Map<String, Object> rerankBody(String model, String query, List<String> documents, int topN) {
        if ("qwen3-rerank".equals(model)) {
            return Map.of(
                    "model", model,
                    "query", query,
                    "documents", documents,
                    "top_n", topN,
                    "return_documents", false);
        }
        return Map.of(
                "model", model,
                "input", Map.of(
                        "query", query,
                        "documents", documents),
                "parameters", Map.of(
                        "return_documents", false,
                        "top_n", topN));
    }

    private List<SearchDtos.SearchHit> applyOrder(List<SearchDtos.SearchHit> hits, String response, int topN) {
        try {
            JsonNode results = objectMapper.readTree(response).path("output").path("results");
            List<ScoredIndex> scored = new ArrayList<>();
            for (JsonNode result : results) {
                scored.add(new ScoredIndex(
                        result.path("index").asInt(-1),
                        result.path("relevance_score").asDouble(result.path("score").asDouble(0))));
            }
            scored.sort(Comparator.comparingDouble(ScoredIndex::score).reversed());

            List<SearchDtos.SearchHit> ordered = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            for (ScoredIndex item : scored) {
                if (item.index() >= 0 && item.index() < hits.size() && used.add(item.index())) {
                    ordered.add(withScore(hits.get(item.index()), item.score()));
                }
                if (ordered.size() == topN) {
                    return ordered;
                }
            }
            for (int index = 0; index < hits.size() && ordered.size() < topN; index++) {
                if (used.add(index)) {
                    ordered.add(hits.get(index));
                }
            }
            return ordered;
        } catch (Exception exception) {
            return hits;
        }
    }

    private SearchDtos.SearchHit withScore(SearchDtos.SearchHit hit, double score) {
        return new SearchDtos.SearchHit(
                hit.documentId(),
                hit.title(),
                hit.originalFilename(),
                hit.status(),
                hit.reason(),
                hit.pageNumber(),
                score);
    }

    private String rerankDocument(SearchDtos.SearchHit hit) {
        StringBuilder builder = new StringBuilder();
        builder.append("标题：").append(hit.title()).append("\n");
        builder.append("文件：").append(hit.originalFilename()).append("\n");
        if (hit.pageNumber() != null) {
            builder.append("页码：").append(hit.pageNumber()).append("\n");
        }
        builder.append("片段：").append(Optional.ofNullable(hit.reason()).orElse(""));
        return builder.toString();
    }

    private record ScoredIndex(int index, double score) {
    }
}
