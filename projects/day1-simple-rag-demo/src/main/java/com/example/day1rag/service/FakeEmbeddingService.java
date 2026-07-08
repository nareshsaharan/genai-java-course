package com.example.day1rag.service;

import org.springframework.stereotype.Service;

/**
 * A FAKE embedding model, built only so students can see how text turns
 * into a vector, without needing a real AI model or API key.
 *
 * IMPORTANT: this is NOT what production systems use. A real embedding
 * model (e.g. OpenAI's text-embedding-3-small, or Anthropic-compatible
 * embedding models) is trained on massive amounts of text so that
 * sentences with similar MEANING end up with similar vectors — even if
 * they don't share any of the same words (e.g. "car" and "automobile").
 * That kind of understanding cannot come from simple hashing.
 *
 * How this fake version works ("word hashing"):
 *   1. Split the text into lowercase words.
 *   2. For each word, compute a hash code and use it to pick one of the
 *      64 "slots" (dimensions) in our vector.
 *   3. Add 1.0 into that slot every time a word lands on it. Words that
 *      happen to hash to the same slot just add up — this is a crude
 *      approximation, nothing more.
 *   4. Normalize the vector (scale it so its length is 1.0). This keeps
 *      vector comparisons fair regardless of how long the text was —
 *      a long chunk and a short chunk with similar word patterns will
 *      still be comparable.
 *
 * Two chunks that share a lot of the same words will end up with
 * similar-looking vectors, which is just enough for students to see
 * "top-k search" work in a later lesson — even though this fake
 * embedding knows nothing about actual meaning.
 */
@Service
public class FakeEmbeddingService implements EmbeddingService {

    private static final int VECTOR_SIZE = 64;

    @Override
    public double[] embed(String text) {
        double[] vector = new double[VECTOR_SIZE];

        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            // Math.floorMod handles negative hash codes correctly,
            // always giving us an index between 0 and VECTOR_SIZE - 1.
            int slot = Math.floorMod(word.hashCode(), VECTOR_SIZE);
            vector[slot] += 1.0;
        }

        return normalize(vector);
    }

    /**
     * Scales the vector so its total length (L2 norm) becomes 1.0.
     * This is a common step for real embeddings too — it makes it fair
     * to compare vectors from short texts against vectors from long
     * texts using cosine similarity.
     */
    private double[] normalize(double[] vector) {
        double sumOfSquares = 0.0;
        for (double value : vector) {
            sumOfSquares += value * value;
        }

        double length = Math.sqrt(sumOfSquares);
        if (length == 0.0) {
            // Empty or all-blank text: return the zero vector as-is
            // rather than dividing by zero.
            return vector;
        }

        double[] normalized = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / length;
        }
        return normalized;
    }
}
