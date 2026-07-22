package com.studybuddy.config;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.studybuddy.config.properties.GeminiProperties;
import com.studybuddy.settings.EmbeddingProvider;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * {@link EmbeddingModel} implementation that reads the currently-selected
 * {@link EmbeddingProvider} and its API key from {@link RuntimeSecretsService}
 * on every call, instead of a provider/key baked in permanently at
 * application startup. Caches a real client keyed by (provider, key),
 * rebuilding only when either changes.
 *
 * <p>Both providers are truncated/configured to output 384 dimensions
 * (OpenAI's {@code text-embedding-3-small} via its own {@code dimensions}
 * parameter, Gemini's {@code gemini-embedding-001} via
 * {@code outputDimensionality}) so the existing
 * {@code course_chunks.embedding VECTOR(384)} column never needs to change.
 * Switching providers means any previously-ingested content should be
 * re-uploaded — vector *values* from a different model aren't comparable
 * even at the same dimension.
 *
 * <p>When no key is configured for the selected provider (Mock Mode — the
 * default for every new session, see {@link RuntimeSecretsService}), no real
 * API call is made: a deterministic pseudo-embedding (hashed from the
 * segment's own text) is returned instead, so document upload keeps working
 * end-to-end with zero API keys — the resulting vectors aren't semantically
 * meaningful, but the ingestion/storage/retrieval pipeline itself stays
 * fully exercised.
 */
@Component
public class DynamicEmbeddingModel implements EmbeddingModel {

    private static final String OPENAI_MODEL_NAME = "text-embedding-3-small";
    private static final int DIMENSIONS = 384;
    private static final Duration OPENAI_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 2;

    private final RuntimeSecretsService secrets;
    private final GeminiProperties geminiProperties;

    private volatile String cachedIdentity;
    private volatile EmbeddingModel cachedModel;

    public DynamicEmbeddingModel(RuntimeSecretsService secrets, GeminiProperties geminiProperties) {
        this.secrets = secrets;
        this.geminiProperties = geminiProperties;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        String currentKey = secrets.getActiveEmbeddingKey();
        if (currentKey == null || currentKey.isBlank()) {
            return mockEmbedAll(textSegments);
        }
        return resolve(secrets.getEmbeddingProvider(), currentKey).embedAll(textSegments);
    }

    @Override
    public int dimension() {
        return DIMENSIONS;
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    EmbeddingModel resolveForTest() {
        return resolve(secrets.getEmbeddingProvider(), secrets.getActiveEmbeddingKey());
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

    private synchronized EmbeddingModel resolve(EmbeddingProvider provider, String currentKey) {
        String identity = provider.name() + "|" + currentKey;
        if (!identity.equals(cachedIdentity)) {
            cachedModel = build(provider, currentKey);
            cachedIdentity = identity;
        }
        return cachedModel;
    }

    private EmbeddingModel build(EmbeddingProvider provider, String apiKey) {
        return switch (provider) {
            case OPENAI -> OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(OPENAI_MODEL_NAME)
                    .dimensions(DIMENSIONS)
                    .timeout(OPENAI_TIMEOUT)
                    .maxRetries(MAX_RETRIES)
                    .build();
            case GEMINI -> GoogleAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(geminiProperties.embeddingModel())
                    .outputDimensionality(DIMENSIONS)
                    .timeout(Duration.ofSeconds(geminiProperties.timeoutSeconds()))
                    .maxRetries(MAX_RETRIES)
                    .build();
        };
    }
}
