package com.example.day1rag.controller;

import com.example.day1rag.model.DocumentIngestRequest;
import com.example.day1rag.service.DocumentService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for document ingestion — steps 1 and 2 of the RAG pipeline
 * (ingestion and chunking).
 *
 * Today, ingesting a document stores it in memory and splits it into
 * chunks. Embedding the chunks is the next lesson.
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/api/documents/ingest")
    public Map<String, Object> ingest(@RequestBody DocumentIngestRequest request) {
        int chunksCreated = documentService.ingest(request);

        // LinkedHashMap keeps insertion order so the JSON output matches
        // the order below: documentId, status, chunksCreated.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documentId", request.getDocumentId());
        response.put("status", "INGESTED");
        response.put("chunksCreated", chunksCreated);
        return response;
    }
}
