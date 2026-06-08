package com.voyager.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyager.docs.config.AppProperties;
import com.voyager.docs.domain.Document;
import com.voyager.docs.domain.DocumentChunk;
import com.voyager.docs.domain.DocumentStatus;
import com.voyager.docs.dto.SearchDtos;
import com.voyager.docs.repository.DocumentChunkRepository;
import com.voyager.docs.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class SearchService {
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final BailianEmbeddingService embeddingService;
    private final BailianRerankService rerankService;

    public SearchService(
            DocumentRepository documents,
            DocumentChunkRepository chunks,
            AppProperties properties,
            ObjectMapper objectMapper,
            BailianEmbeddingService embeddingService,
            BailianRerankService rerankService) {
        this.documents = documents;
        this.chunks = chunks;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.restClient = RestClient.builder().baseUrl(properties.getOpensearch().getEndpoint()).build();
    }

    public SearchDtos.SearchResponse aiSearch(SearchDtos.AiSearchRequest request) {
        int limit = request.limit() == null ? 8 : Math.max(1, Math.min(request.limit(), 20));
        List<SearchDtos.SearchHit> hits = searchOpenSearch(request.query(), limit);
        if (!hits.isEmpty()) {
            return new SearchDtos.SearchResponse(request.query(), hits, "opensearch");
        }
        List<SearchDtos.SearchHit> fallback = searchLocalFallback(request.query(), limit);
        return new SearchDtos.SearchResponse(request.query(), fallback, "local-fallback");
    }

    private List<SearchDtos.SearchHit> searchOpenSearch(String query, int limit) {
        Map<UUID, SearchDtos.SearchHit> merged = new LinkedHashMap<>();
        searchOpenSearchText(query, limit).forEach(hit -> merged.put(hit.documentId(), hit));
        embeddingService.queryTextEmbedding(query)
                .map(vector -> searchOpenSearchVector("content_vector", vector, limit))
                .orElse(List.of())
                .forEach(hit -> merged.putIfAbsent(hit.documentId(), hit));
        embeddingService.queryVisualEmbedding(query)
                .map(vector -> searchOpenSearchVector("visual_vector", vector, limit))
                .orElse(List.of())
                .forEach(hit -> merged.putIfAbsent(hit.documentId(), hit));
        List<SearchDtos.SearchHit> hits = filterLiveDocuments(
                merged.values().stream().limit(Math.max(limit, 20)).toList());
        return rerankService.rerank(query, hits, limit);
    }

    private List<SearchDtos.SearchHit> searchOpenSearchText(String query, int limit) {
        try {
            Map<String, Object> body = Map.of(
                    "size", limit,
                    "query", Map.of(
                            "multi_match", Map.of(
                                    "query", query,
                                    "fields", List.of("title^3", "content", "ocr_text", "caption"))));
            String response = restClient.post()
                    .uri("/{index}/_search", properties.getOpensearch().getDocumentIndex())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseOpenSearchHits(response);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<SearchDtos.SearchHit> searchOpenSearchVector(String field, List<Double> vector, int limit) {
        try {
            Map<String, Object> body = Map.of(
                    "size", limit,
                    "query", Map.of(
                            "knn", Map.of(
                                    field, Map.of(
                                            "vector", vector,
                                            "k", limit))));
            String response = restClient.post()
                    .uri("/{index}/_search", properties.getOpensearch().getDocumentIndex())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseOpenSearchHits(response);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<SearchDtos.SearchHit> parseOpenSearchHits(String response) {
        try {
            JsonNode nodes = objectMapper.readTree(response).path("hits").path("hits");
            List<SearchDtos.SearchHit> hits = new ArrayList<>();
            for (JsonNode hit : nodes) {
                JsonNode source = hit.path("_source");
                UUID documentId = UUID.fromString(source.path("document_id").asText());
                hits.add(new SearchDtos.SearchHit(
                        documentId,
                        source.path("title").asText(""),
                        source.path("original_filename").asText(""),
                        source.path("status").asText("READY"),
                        source.path("content").asText(source.path("ocr_text").asText("")),
                        source.path("page_number").isMissingNode() || source.path("page_number").isNull()
                                ? null
                                : source.path("page_number").asInt(),
                        hit.path("_score").asDouble(0)));
            }
            return hits;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<SearchDtos.SearchHit> searchLocalFallback(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Map<UUID, SearchDtos.SearchHit> merged = new LinkedHashMap<>();
        documents.findTop50ByDeletedAtIsNullAndRecycledAtIsNullAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(query.trim())
                .stream()
                .filter(this::isSearchVisible)
                .forEach(document -> merged.put(document.getId(), hitFromDocument(document, "Title match", 1.0)));
        for (DocumentChunk chunk : chunks.findTop8ByContentContainingIgnoreCaseOrderByCreatedAtDesc(query.trim())) {
            Document document = chunk.getDocument();
            if (!isSearchVisible(document)) {
                continue;
            }
            merged.putIfAbsent(document.getId(), new SearchDtos.SearchHit(
                    document.getId(),
                    document.getTitle(),
                    document.getOriginalFilename(),
                    document.getStatus().name(),
                    trim(chunk.getContent()),
                    chunk.getPageNumber(),
                    0.7));
        }
        return merged.values().stream().limit(limit).toList();
    }

    private List<SearchDtos.SearchHit> filterLiveDocuments(List<SearchDtos.SearchHit> hits) {
        if (hits.isEmpty()) {
            return hits;
        }
        Set<UUID> liveIds = documents.findAllById(hits.stream().map(SearchDtos.SearchHit::documentId).distinct().toList())
                .stream()
                .filter(this::isSearchVisible)
                .map(Document::getId)
                .collect(Collectors.toSet());
        return hits.stream()
                .filter(hit -> liveIds.contains(hit.documentId()))
                .toList();
    }

    private boolean isSearchVisible(Document document) {
        return document.getDeletedAt() == null
                && document.getRecycledAt() == null
                && document.getStatus() == DocumentStatus.READY;
    }

    private SearchDtos.SearchHit hitFromDocument(Document document, String reason, double score) {
        return new SearchDtos.SearchHit(
                document.getId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getStatus().name(),
                reason,
                null,
                score);
    }

    private String trim(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 220 ? content.substring(0, 220) + "..." : content;
    }
}
