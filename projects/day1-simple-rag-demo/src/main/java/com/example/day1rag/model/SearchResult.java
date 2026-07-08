package com.example.day1rag.model;

import java.util.Map;

/**
 * One retrieved chunk, returned as part of a SearchResponse.
 *
 * "metadata" here is a small, API-friendly view (documentId, title,
 * chunkIndex) — not the exact same Map stored on DocumentChunk, which
 * also carries a "source" field and stores chunkIndex as a String.
 * RetrievalService builds this cleaner shape for the response JSON.
 */
public class SearchResult {

    private final String chunkText;
    private final double score;
    private final Map<String, Object> metadata;

    public SearchResult(String chunkText, double score, Map<String, Object> metadata) {
        this.chunkText = chunkText;
        this.score = score;
        this.metadata = metadata;
    }

    public String getChunkText() {
        return chunkText;
    }

    public double getScore() {
        return score;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
