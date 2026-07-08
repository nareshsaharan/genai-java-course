package com.example.day1rag.controller;

import com.example.day1rag.model.RagRequest;
import com.example.day1rag.model.RagResponse;
import com.example.day1rag.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the full RAG pipeline: retrieve relevant chunks,
 * then generate an answer grounded in them.
 *
 * This ties together everything built so far: chunking, embeddings,
 * vector search, and (mock) answer generation, all behind one
 * beginner-friendly endpoint.
 */
@RestController
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/api/rag/ask")
    public RagResponse ask(@RequestBody RagRequest request) {
        return ragService.ask(request);
    }
}
