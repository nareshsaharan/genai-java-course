package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.studybuddy.config.properties.GeminiProperties;
import com.studybuddy.settings.EmbeddingProvider;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;

class DynamicEmbeddingModelTest {

    private static GeminiProperties geminiProperties() {
        return new GeminiProperties("gemini-2.0-flash", "gemini-embedding-001", 30);
    }

    private static DynamicEmbeddingModel model(RuntimeSecretsService secrets) {
        return new DynamicEmbeddingModel(secrets, geminiProperties());
    }

    private static RuntimeSecretsService mockSecrets(EmbeddingProvider provider, String key) {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getEmbeddingProvider()).thenReturn(provider);
        when(secrets.getActiveEmbeddingKey()).thenReturn(key);
        return secrets;
    }

    @Test
    void reportedDimensionIs384WithoutMakingARealCall() {
        RuntimeSecretsService secrets = mockSecrets(EmbeddingProvider.OPENAI, "sk-test-key-aaaa");

        assertThat(model(secrets).dimension()).isEqualTo(384);
    }

    @Test
    void returnsDeterministicMockEmbeddingWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(EmbeddingProvider.OPENAI, null);
        DynamicEmbeddingModel model = model(secrets);

        Response<List<Embedding>> first = model.embedAll(List.of(TextSegment.from("test")));
        Response<List<Embedding>> second = model.embedAll(List.of(TextSegment.from("test")));

        assertThat(first.content()).hasSize(1);
        assertThat(first.content().get(0).vector()).hasSize(384);
        assertThat(first.content().get(0).vector())
                .as("same text hashes to the same mock vector")
                .isEqualTo(second.content().get(0).vector());
    }

    @Test
    void reusesCachedClientWhenProviderAndKeyHaveNotChanged() {
        RuntimeSecretsService secrets = mockSecrets(EmbeddingProvider.OPENAI, "sk-test-key-aaaa");
        DynamicEmbeddingModel model = model(secrets);

        var first = model.resolveForTest();
        var second = model.resolveForTest();

        assertThat(first).isSameAs(second);
    }

    @Test
    void rebuildsClientWhenKeyChanges() {
        java.util.concurrent.atomic.AtomicReference<String> key =
                new java.util.concurrent.atomic.AtomicReference<>("sk-test-key-aaaa");
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getEmbeddingProvider()).thenReturn(EmbeddingProvider.OPENAI);
        when(secrets.getActiveEmbeddingKey()).thenAnswer(invocation -> key.get());
        DynamicEmbeddingModel model = model(secrets);

        var first = model.resolveForTest();
        key.set("sk-test-key-bbbb");
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void rebuildsClientWhenProviderChangesEvenIfKeyStringIsTheSame() {
        java.util.concurrent.atomic.AtomicReference<EmbeddingProvider> provider =
                new java.util.concurrent.atomic.AtomicReference<>(EmbeddingProvider.OPENAI);
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getEmbeddingProvider()).thenAnswer(invocation -> provider.get());
        when(secrets.getActiveEmbeddingKey()).thenReturn("same-key-value");
        DynamicEmbeddingModel model = model(secrets);

        var first = model.resolveForTest();
        provider.set(EmbeddingProvider.GEMINI);
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void buildsAGeminiEmbeddingClientWhenGeminiIsSelected() {
        RuntimeSecretsService secrets = mockSecrets(EmbeddingProvider.GEMINI, "gemini-test-key");

        var resolved = model(secrets).resolveForTest();

        assertThat(resolved).isInstanceOf(dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.class);
    }
}
