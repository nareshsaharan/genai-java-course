package com.example.day1rag.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.day1rag.service.EmbeddingService;
import com.example.day1rag.service.FakeEmbeddingService;
import org.junit.jupiter.api.Test;

/**
 * A small, beginner-friendly demonstration of CosineSimilarity: turn two
 * words into (fake) embeddings, then measure how similar their vectors are.
 *
 * Note: FakeEmbeddingService only looks at which words appear (word
 * hashing) — it has no real understanding of meaning. In this test,
 * "the car is fast" scores higher against "the automobile is fast"
 * than against "i ate a banana" simply because the first pair shares
 * three common words ("the", "is", "fast") — NOT because the fake
 * embedding understands that a car and an automobile are the same
 * thing. A future lesson swaps in a real embedding model, and these
 * same CosineSimilarity calls will start reflecting actual meaning.
 */
class CosineSimilarityTest {

    private final EmbeddingService embeddingService = new FakeEmbeddingService();
    private final CosineSimilarity cosineSimilarity = new CosineSimilarity();

    @Test
    void identicalTextIsPerfectlySimilar() {
        double[] vectorA = embeddingService.embed("car");
        double[] vectorB = embeddingService.embed("car");

        double score = cosineSimilarity.calculate(vectorA, vectorB);

        // The exact same text always produces the exact same vector,
        // so the similarity score should be 1.0 (identical direction).
        assertEquals(1.0, score, 0.0001);
    }

    @Test
    void printsSimilarityBetweenDifferentWordPairs() {
        // Short phrases (not single words) so shared words like "the"
        // and "is" give the fake embedding something to match on.
        double carVsAutomobile = similarity("the car is fast", "the automobile is fast");
        double carVsBanana = similarity("the car is fast", "i ate a banana");

        System.out.println("similarity(\"the car is fast\", \"the automobile is fast\") = " + carVsAutomobile);
        System.out.println("similarity(\"the car is fast\", \"i ate a banana\") = " + carVsBanana);

        // Both scores are valid cosine similarity values, between -1 and 1.
        assertEquals(true, carVsAutomobile >= -1.0 && carVsAutomobile <= 1.0);
        assertEquals(true, carVsBanana >= -1.0 && carVsBanana <= 1.0);
    }

    private double similarity(String textA, String textB) {
        double[] vectorA = embeddingService.embed(textA);
        double[] vectorB = embeddingService.embed(textB);
        return cosineSimilarity.calculate(vectorA, vectorB);
    }
}
