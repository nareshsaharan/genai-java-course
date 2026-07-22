package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.studybuddy.common.exception.EmbeddingsNotConfiguredException;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.segment.TextSegment;

class DynamicOpenAiEmbeddingModelTest {

    @Test
    void reportedDimensionIs384WithoutMakingARealCall() {
        RuntimeSecretsService secrets = mockSecrets("sk-test-key-aaaa");
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        assertThat(model.dimension()).isEqualTo(384);
    }

    @Test
    void throwsEmbeddingsNotConfiguredWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicOpenAiEmbeddingModel model = new DynamicOpenAiEmbeddingModel(secrets);

        assertThatThrownBy(() -> model.embedAll(List.of(TextSegment.from("test"))))
                .isInstanceOf(EmbeddingsNotConfiguredException.class);
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
