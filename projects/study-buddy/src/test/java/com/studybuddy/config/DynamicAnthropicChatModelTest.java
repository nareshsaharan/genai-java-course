package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ClaudeNotConfiguredException;
import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

class DynamicAnthropicChatModelTest {

    private ClaudeProperties properties() {
        return new ClaudeProperties(null, "claude-sonnet-5", null, 1024, 30);
    }

    @Test
    void throwsClaudeNotConfiguredWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        assertThatThrownBy(() -> model.doChat(request()))
                .isInstanceOf(ClaudeNotConfiguredException.class);
    }

    @Test
    void reusesCachedClientWhenKeyHasNotChanged() {
        RuntimeSecretsService secrets = mockSecrets("sk-ant-test-key-aaaa");
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        var first = model.resolveForTest();
        var second = model.resolveForTest();

        assertThat(first).isSameAs(second);
    }

    @Test
    void rebuildsClientWhenKeyChanges() {
        java.util.concurrent.atomic.AtomicReference<String> key =
                new java.util.concurrent.atomic.AtomicReference<>("sk-ant-test-key-aaaa");
        RuntimeSecretsService secrets = org.mockito.Mockito.mock(RuntimeSecretsService.class);
        org.mockito.Mockito.when(secrets.getAnthropicKey()).thenAnswer(invocation -> key.get());
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        var first = model.resolveForTest();
        key.set("sk-ant-test-key-bbbb");
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    private static RuntimeSecretsService mockSecrets(String key) {
        RuntimeSecretsService secrets = org.mockito.Mockito.mock(RuntimeSecretsService.class);
        org.mockito.Mockito.when(secrets.getAnthropicKey()).thenReturn(key);
        return secrets;
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("test")).build();
    }
}
