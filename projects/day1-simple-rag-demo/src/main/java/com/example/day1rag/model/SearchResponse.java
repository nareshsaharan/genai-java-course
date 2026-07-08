package com.example.day1rag.model;

import java.util.List;

/**
 * The JSON body returned by POST /api/search.
 *
 * Note: this only RETRIEVES the most relevant chunks — it does not
 * generate a final answer. Turning these chunks into an answer using
 * an LLM is the next lesson.
 */
public class SearchResponse {

    private final String query;
    private final List<SearchResult> results;

    public SearchResponse(String query, List<SearchResult> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public List<SearchResult> getResults() {
        return results;
    }
}
