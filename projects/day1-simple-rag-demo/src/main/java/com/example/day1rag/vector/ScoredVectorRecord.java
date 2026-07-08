package com.example.day1rag.vector;

import com.example.day1rag.model.VectorRecord;

/**
 * A search result before it's turned into API JSON: a stored chunk +
 * vector, paired with how similar it was to the search query.
 *
 * This is an internal helper for InMemoryVectorStore's search() method.
 * RetrievalService converts these into the SearchResult shape that
 * actually gets returned to API callers.
 */
public class ScoredVectorRecord {

    private final VectorRecord vectorRecord;
    private final double score;

    public ScoredVectorRecord(VectorRecord vectorRecord, double score) {
        this.vectorRecord = vectorRecord;
        this.score = score;
    }

    public VectorRecord getVectorRecord() {
        return vectorRecord;
    }

    public double getScore() {
        return score;
    }
}
