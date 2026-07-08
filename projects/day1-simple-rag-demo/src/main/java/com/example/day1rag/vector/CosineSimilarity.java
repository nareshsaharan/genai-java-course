package com.example.day1rag.vector;

import org.springframework.stereotype.Component;

/**
 * Measures how "close" two vectors are, using cosine similarity.
 *
 * Imagine each vector as an arrow pointing in some direction in space.
 * Cosine similarity looks at the ANGLE between two arrows, not their
 * length:
 *   - If the arrows point in exactly the same direction, the angle is
 *     0 degrees, and the score is 1.0 (as close/similar as possible).
 *   - If the arrows point in completely unrelated directions
 *     (90 degrees apart), the score is 0.0 (unrelated).
 *   - If the arrows point in opposite directions, the score is -1.0
 *     (as different as possible) — this mostly shows up with vectors
 *     that can be negative, less common with our word-count-style
 *     fake embeddings, but the formula still handles it correctly.
 *
 * In short: higher score = more similar. A score near 1 means the two
 * pieces of text are highly similar (at least according to whatever
 * embedding model produced the vectors); a score near 0 means they're
 * not very similar. This is exactly what top-k search (a later lesson)
 * will use to find the chunks most relevant to a user's question.
 *
 * No external math libraries needed — just the formula:
 *
 *   cosine similarity = (A . B) / (|A| * |B|)
 *
 * where "A . B" is the dot product (multiply matching positions, then
 * sum them) and "|A|" is the length (magnitude) of vector A.
 */
@Component
public class CosineSimilarity {

    public double calculate(double[] a, double[] b) {
        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            magnitudeA += a[i] * a[i];
            magnitudeB += b[i] * b[i];
        }

        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);

        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            // A zero vector has no direction, so "similarity" is
            // undefined. Return 0.0 (treat it as unrelated) instead of
            // dividing by zero.
            return 0.0;
        }

        return dotProduct / (magnitudeA * magnitudeB);
    }
}
