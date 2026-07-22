package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Confirms the shared embedding model — used for both ingestion and
 * retrieval — reports 384-dimensional vectors, matching the
 * course_chunks.embedding VECTOR(384) column. If this ever drifts (e.g. a
 * model swap), ingestion/retrieval would fail loudly at the DB layer, but
 * this test catches the mismatch immediately and cheaply, with no DB and no
 * real API call needed.
 */
class EmbeddingDimensionTest {

    @Test
    void reportedDimensionMatches384() {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn("sk-test-key");
        EmbeddingModel embeddingModel = new DynamicOpenAiEmbeddingModel(secrets);

        assertThat(embeddingModel.dimension()).isEqualTo(384);
    }
}
