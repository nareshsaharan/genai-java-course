package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

/**
 * Confirms the shared embedding model — used for both ingestion and
 * retrieval — actually produces 384-dimensional vectors, matching the
 * course_chunks.embedding VECTOR(384) column. If this ever drifts (e.g. a
 * model swap), ingestion/retrieval would fail loudly at the DB layer, but
 * this test catches the mismatch immediately and cheaply, with no DB needed.
 */
class EmbeddingDimensionTest {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    @Test
    void producesA384DimensionEmbedding() {
        float[] vector = embeddingModel.embed("Dependency injection is a design pattern.").content().vector();

        assertThat(vector).hasSize(384);
    }

    @Test
    void reportedDimensionMatches384() {
        assertThat(embeddingModel.dimension()).isEqualTo(384);
    }
}
