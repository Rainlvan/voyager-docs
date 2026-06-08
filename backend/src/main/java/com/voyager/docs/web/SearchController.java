package com.voyager.docs.web;

import com.voyager.docs.dto.DocumentDtos;
import com.voyager.docs.dto.SearchDtos;
import com.voyager.docs.service.DocumentService;
import com.voyager.docs.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final DocumentService documentService;
    private final SearchService searchService;

    public SearchController(DocumentService documentService, SearchService searchService) {
        this.documentService = documentService;
        this.searchService = searchService;
    }

    @GetMapping("/title")
    public List<DocumentDtos.DocumentResponse> titleSearch(@RequestParam("q") String query) {
        return documentService.titleSearch(query);
    }

    @PostMapping("/ai")
    public SearchDtos.SearchResponse aiSearch(@Valid @RequestBody SearchDtos.AiSearchRequest request) {
        return searchService.aiSearch(request);
    }
}
