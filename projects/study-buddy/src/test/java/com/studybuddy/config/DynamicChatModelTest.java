package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.config.properties.GeminiProperties;
import com.studybuddy.config.properties.GroqProperties;
import com.studybuddy.config.properties.OpenRouterProperties;
import com.studybuddy.settings.ChatProvider;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

class DynamicChatModelTest {

    private static ClaudeProperties claudeProperties() {
        return new ClaudeProperties(null, "claude-sonnet-5", null, 1024, 30);
    }

    private static GroqProperties groqProperties() {
        return new GroqProperties("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", 30);
    }

    private static OpenRouterProperties openRouterProperties() {
        return new OpenRouterProperties("https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", 30);
    }

    private static GeminiProperties geminiProperties() {
        return new GeminiProperties("gemini-flash-latest", "gemini-embedding-001", 30);
    }

    private static DynamicChatModel model(RuntimeSecretsService secrets) {
        return new DynamicChatModel(secrets, claudeProperties(), groqProperties(), openRouterProperties(), geminiProperties());
    }

    private static RuntimeSecretsService mockSecrets(ChatProvider provider, String key) {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getChatProvider()).thenReturn(provider);
        when(secrets.getActiveChatKey()).thenReturn(key);
        return secrets;
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("test")).build();
    }

    @Test
    void returnsMockAnswerWhenNoKeyIsSetForTheSelectedProvider() {
        RuntimeSecretsService secrets = mockSecrets(ChatProvider.CLAUDE, null);

        ChatResponse response = model(secrets).doChat(request());

        assertThat(response.aiMessage().text()).containsIgnoringCase("mock mode");
    }

    @Test
    void returnsMockFlashcardJsonWhenPromptAsksForCardsAndNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(ChatProvider.GROQ, null);
        ChatRequest flashcardRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Respond with JSON: {\"cards\": [...]}"),
                        UserMessage.from("Generate flashcards"))
                .build();

        ChatResponse response = model(secrets).doChat(flashcardRequest);

        assertThat(response.aiMessage().text()).contains("\"cards\"");
    }

    @Test
    void returnsMockQuizJsonWhenPromptAsksForCorrectOptionIndexAndNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(ChatProvider.OPENROUTER, null);
        ChatRequest quizRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Respond with JSON: {\"questions\": [{\"correctOptionIndex\": 0}]}"),
                        UserMessage.from("Generate a quiz"))
                .build();

        ChatResponse response = model(secrets).doChat(quizRequest);

        assertThat(response.aiMessage().text()).contains("\"questions\"").contains("correctOptionIndex");
    }

    @Test
    void reusesCachedClientWhenProviderAndKeyHaveNotChanged() {
        RuntimeSecretsService secrets = mockSecrets(ChatProvider.CLAUDE, "sk-ant-test-key-aaaa");
        DynamicChatModel model = model(secrets);

        var first = model.resolveForTest();
        var second = model.resolveForTest();

        assertThat(first).isSameAs(second);
    }

    @Test
    void rebuildsClientWhenKeyChanges() {
        java.util.concurrent.atomic.AtomicReference<String> key =
                new java.util.concurrent.atomic.AtomicReference<>("sk-ant-test-key-aaaa");
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getChatProvider()).thenReturn(ChatProvider.CLAUDE);
        when(secrets.getActiveChatKey()).thenAnswer(invocation -> key.get());
        DynamicChatModel model = model(secrets);

        var first = model.resolveForTest();
        key.set("sk-ant-test-key-bbbb");
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void rebuildsClientWhenProviderChangesEvenIfKeyStringIsTheSame() {
        java.util.concurrent.atomic.AtomicReference<ChatProvider> provider =
                new java.util.concurrent.atomic.AtomicReference<>(ChatProvider.GROQ);
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getChatProvider()).thenAnswer(invocation -> provider.get());
        when(secrets.getActiveChatKey()).thenReturn("same-key-value");
        DynamicChatModel model = model(secrets);

        var first = model.resolveForTest();
        provider.set(ChatProvider.OPENROUTER);
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void buildsAGeminiClientWhenGeminiIsSelected() {
        RuntimeSecretsService secrets = mockSecrets(ChatProvider.GEMINI, "gemini-test-key");

        var resolved = model(secrets).resolveForTest();

        assertThat(resolved).isInstanceOf(dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.class);
    }
}
