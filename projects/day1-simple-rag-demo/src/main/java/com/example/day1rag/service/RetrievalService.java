package com.example.day1rag.service;

import com.example.day1rag.model.DocumentChunk;
import com.example.day1rag.model.SearchRequest;
import com.example.day1rag.model.SearchResponse;
import com.example.day1rag.model.SearchResult;
import com.example.day1rag.vector.InMemoryVectorStore;
import com.example.day1rag.vector.ScoredVectorRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Step 5 of RAG: "retrieval" — given a question, find the most relevant
 * stored chunks.
 *
 * IMPORTANT for students: this service only RETRIEVES chunks. It does
 * NOT generate a final answer. Combining these chunks with an LLM to
 * produce a human-readable answer is "RAG answer generation" (step 6),
 * which is the next lesson.
 */
@Service
public class RetrievalService {

    private final InMemoryVectorStore vectorStore;

    public RetrievalService(InMemoryVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SearchResponse search(SearchRequest request) {
        List<ScoredVectorRecord> topMatches = vectorStore.search(request.getQuery(), request.getTopK());

        List<SearchResult> results = topMatches.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        return new SearchResponse(request.getQuery(), results);
    }

    private SearchResult toSearchResult(ScoredVectorRecord scoredVectorRecord) {
        DocumentChunk chunk = scoredVectorRecord.getVectorRecord().getChunk();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("title", chunk.getTitle());
        metadata.put("chunkIndex", chunk.getChunkIndex());

        return new SearchResult(chunk.getText(), scoredVectorRecord.getScore(), metadata);
    }
}
