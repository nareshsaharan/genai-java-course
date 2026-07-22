package com.studybuddy.config;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.EmbeddingsNotConfiguredException;
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
        return resolve().embedAll(textSegments);
    }

    @Override
    public int dimension() {
        return DIMENSIONS;
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    OpenAiEmbeddingModel resolveForTest() {
        return resolve();
    }

    private synchronized OpenAiEmbeddingModel resolve() {
        String currentKey = secrets.getOpenAiKey();
        if (currentKey == null || currentKey.isBlank()) {
            throw new EmbeddingsNotConfiguredException(
                    "Embeddings are not configured — add an OpenAI API key in Settings to enable this.");
        }
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
