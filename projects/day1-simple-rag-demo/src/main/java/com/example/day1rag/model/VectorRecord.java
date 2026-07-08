package com.example.day1rag.model;

/**
 * Pairs a chunk of text with its embedding (vector).
 *
 * This is what will eventually live in the vector store: not just the
 * raw text, but the numbers that represent its meaning, so we can later
 * compare a question's vector against every stored vector to find the
 * closest (most relevant) chunks — that's "top-k search", coming in a
 * future lesson.
 */
public class VectorRecord {

    private final DocumentChunk chunk;
    private final double[] vector;

    public VectorRecord(DocumentChunk chunk, double[] vector) {
        this.chunk = chunk;
        this.vector = vector;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public double[] getVector() {
        return vector;
    }
}
