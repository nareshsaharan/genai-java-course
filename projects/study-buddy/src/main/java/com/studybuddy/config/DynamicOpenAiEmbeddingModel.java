package com.studybuddy.config;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * {@link EmbeddingModel} implementation that reads the currently-active
 * OpenAI API key from {@link RuntimeSecretsService} on every call, instead of
 * a key baked in permanently at application startup — mirrors
 * {@link DynamicAnthropicChatModel}. Caches a real {@link OpenAiEmbeddingModel}
 * keyed by the current key value, rebuilding only when the key changes.
 *
 * <p>Uses OpenAI's {@code text-embedding-3-small}, truncated to 384
 * dimensions via the API's own {@code dimensions} parameter so the existing
 * {@code course_chunks.embedding VECTOR(384)} column never needs to change.
 * Replaces the project's original local in-process ONNX model
 * (all-MiniLM-L6-v2): that model's native (off-heap) memory footprint while
 * loading doesn't fit in a 512MB container on free-tier PaaS hosts, a
 * constraint that didn't exist when the local-model decision was first made.
 * Any course notes ingested under the old model must be re-uploaded — the
 * vector *values* from a different model are not comparable even at the same
 * dimension.
 *
 * <p>When no key is configured for the session (Mock Mode — the default for
 * every new session, see {@link RuntimeSecretsService}), no real API call is
 * made: a deterministic pseudo-embedding (hashed from the segment's own text)
 * is returned instead, so document upload keeps working end-to-end with zero
 * API keys — the resulting vectors aren't semantically meaningful, but the
 * ingestion/storage/retrieval pipeline itself stays fully exercised.
 */
@Component
public class DynamicOpenAiEmbeddingModel implements EmbeddingModel {

    private static final String MODEL_NAME = "text-embedding-3-small";
    private static final int DIMENSIONS = 384;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 2;

    private final RuntimeSecretsService secrets;

    private volatile String cachedKey;
    private volatile OpenAiEmbeddingModel cachedModel;

    public DynamicOpenAiEmbeddingModel(RuntimeSecretsService secrets) {
        this.secrets = secrets;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        String currentKey = secrets.getOpenAiKey();
        if (currentKey == null || currentKey.isBlank()) {
            return mockEmbedAll(textSegments);
        }
        return resolve(currentKey).embedAll(textSegments);
    }

    @Override
    public int dimension() {
        return DIMENSIONS;
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    OpenAiEmbeddingModel resolveForTest() {
        return resolve(secrets.getOpenAiKey());
    }

    private static Response<List<Embedding>> mockEmbedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = textSegments.stream()
                .map(segment -> Embedding.from(deterministicVector(segment.text())))
                .toList();
        return Response.from(embeddings);
    }

    /** Same text always hashes to the same vector, so re-uploading the same document is idempotent even in Mock Mode. */
    private static float[] deterministicVector(String text) {
        Random random = new Random(text.hashCode());
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (random.nextFloat() * 2) - 1;
        }
        return vector;
    }

    private synchronized OpenAiEmbeddingModel resolve(String currentKey) {
        if (!currentKey.equals(cachedKey)) {
            cachedModel = build(currentKey);
            cachedKey = currentKey;
        }
        return cachedModel;
    }

    private static OpenAiEmbeddingModel build(String apiKey) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .dimensions(DIMENSIONS)
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }
}
