package com.voyager.docs.service;

import com.voyager.docs.config.AppProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenSearchIndexService {
    private final AppProperties properties;
    private final RestClient restClient;

    public OpenSearchIndexService(AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getOpensearch().getEndpoint()).build();
    }

    public void deleteDocument(UUID documentId) {
        try {
            restClient.post()
                    .uri("/{index}/_delete_by_query", properties.getOpensearch().getDocumentIndex())
                    .body(Map.of("query", Map.of("term", Map.of("document_id", documentId.toString()))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // OpenSearch cleanup should not block database deletion in local development.
        }
    }

    public void deleteIndex() {
        try {
            restClient.delete()
                    .uri("/{index}", properties.getOpensearch().getDocumentIndex())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Restore can continue even if the index is already absent or OpenSearch is temporarily offline.
        }
    }
}
