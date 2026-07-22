package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

class DynamicAnthropicChatModelTest {

    private ClaudeProperties properties() {
        return new ClaudeProperties(null, "claude-sonnet-5", null, 1024, 30);
    }

    @Test
    void returnsMockAnswerWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        ChatResponse response = model.doChat(request());

        assertThat(response.aiMessage().text()).containsIgnoringCase("mock mode");
    }

    @Test
    void returnsMockFlashcardJsonWhenPromptAsksForCardsAndNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());
        ChatRequest flashcardRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Respond with JSON: {\"cards\": [...]}"),
                        UserMessage.from("Generate flashcards"))
                .build();

        ChatResponse response = model.doChat(flashcardRequest);

        assertThat(response.aiMessage().text()).contains("\"cards\"");
    }

    @Test
    void returnsMockQuizJsonWhenPromptAsksForCorrectOptionIndexAndNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());
        ChatRequest quizRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Respond with JSON: {\"questions\": [{\"correctOptionIndex\": 0}]}"),
                        UserMessage.from("Generate a quiz"))
                .build();

        ChatResponse response = model.doChat(quizRequest);

        assertThat(response.aiMessage().text()).contains("\"questions\"").contains("correctOptionIndex");
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
