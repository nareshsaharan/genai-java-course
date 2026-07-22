package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;

class DynamicOpenAiEmbeddingModelTest {

    @Test
    void reportedDimensionIs384WithoutMakingARealCall() {
        RuntimeSecretsService secrets = mockSecrets("sk-test-key-aaaa");
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        assertThat(model.dimension()).isEqualTo(384);
    }

    @Test
    void returnsDeterministicMockEmbeddingWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        Response<List<Embedding>> first = model.embedAll(List.of(TextSegment.from("test")));
        Response<List<Embedding>> second = model.embedAll(List.of(TextSegment.from("test")));

        assertThat(first.content()).hasSize(1);
        assertThat(first.content().get(0).vector()).hasSize(384);
        assertThat(first.content().get(0).vector())
                .as("same text hashes to the same mock vector")
                .isEqualTo(second.content().get(0).vector());
    }

    @Test
    void reusesCachedClientWhenKeyHasNotChanged() {
        RuntimeSecretsService secrets = mockSecrets("sk-test-key-aaaa");
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        var first = model.resolveForTest();
        var second = model.resolveForTest();

        assertThat(first).isSameAs(second);
    }

    @Test
    void rebuildsClientWhenKeyChanges() {
        java.util.concurrent.atomic.AtomicReference<String> key =
                new java.util.concurrent.atomic.AtomicReference<>("sk-test-key-aaaa");
        RuntimeSecretsService secrets = Mockito.mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenAnswer(invocation -> key.get());
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        var first = model.resolveForTest();
        key.set("sk-test-key-bbbb");
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    private static RuntimeSecretsService mockSecrets(String key) {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn(key);
        return secrets;
    }
}
