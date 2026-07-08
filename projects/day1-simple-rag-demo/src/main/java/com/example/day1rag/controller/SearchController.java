package com.example.day1rag.controller;

import com.example.day1rag.model.SearchRequest;
import com.example.day1rag.model.SearchResponse;
import com.example.day1rag.service.RetrievalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for step 5 of RAG: retrieval / search.
 *
 * Classroom note: this endpoint only RETRIEVES the most relevant
 * chunks for a query — it does not generate a final, human-readable
 * answer. That's the next lesson ("RAG answer generation"), where an
 * LLM combines these retrieved chunks with the original question.
 */
@RestController
public class SearchController {

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/api/search")
    public SearchResponse search(@RequestBody SearchRequest request) {
        return retrievalService.search(request);
    }
}
