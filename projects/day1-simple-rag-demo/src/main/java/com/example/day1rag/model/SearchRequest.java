package com.example.day1rag.model;

/**
 * The JSON body a client sends to POST /api/search.
 *
 * Example JSON:
 * {
 *   "query": "What is RAG?",
 *   "topK": 3
 * }
 */
public class SearchRequest {

    private String query;
    private int topK;

    // Spring needs a no-args constructor to build this object from JSON.
    public SearchRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
