package com.studybuddy.config;

import java.time.Duration;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * {@link ChatModel} implementation that reads the currently-active Anthropic
 * API key from {@link RuntimeSecretsService} on every call, instead of
 * having a key baked in permanently at application startup. Caches a real
 * {@link AnthropicChatModel} keyed by the current key value, rebuilding only
 * when the key actually changes (e.g. after a Settings save) — so a normal
 * request pays no extra cost beyond the first one after a key change.
 * Replaces the old fixed {@code ChatModelConfig} bean; {@code TutorAssistant}
 * / {@code FlashcardGenerator} / {@code QuizGenerator} are unaffected since
 * they only depend on the {@link ChatModel} interface.
 *
 * <p>When no key is configured for the session (Mock Mode — the default for
 * every new session, see {@link RuntimeSecretsService}), no real API call is
 * made at all: a canned {@link ChatResponse} is returned instead, so Tutor,
 * Flashcards, and Quiz all stay fully usable with zero API keys.
 */
@Component
public class DynamicAnthropicChatModel implements ChatModel {

    private final RuntimeSecretsService secrets;
    private final ClaudeProperties properties;

    private volatile String cachedKey;
    private volatile AnthropicChatModel cachedModel;

    public DynamicAnthropicChatModel(RuntimeSecretsService secrets, ClaudeProperties properties) {
        this.secrets = secrets;
        this.properties = properties;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String currentKey = secrets.getAnthropicKey();
        if (currentKey == null || currentKey.isBlank()) {
            return mockResponse(chatRequest);
        }
        return resolve(currentKey).chat(chatRequest);
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    AnthropicChatModel resolveForTest() {
        return resolve(secrets.getAnthropicKey());
    }

    private static ChatResponse mockResponse(ChatRequest chatRequest) {
        String combinedText = chatRequest.messages().stream()
                .map(DynamicAnthropicChatModel::extractText)
                .collect(Collectors.joining("\n"));
        return ChatResponse.builder()
                .aiMessage(new AiMessage(MockAiResponses.forPrompt(combinedText)))
                .build();
    }

    private static String extractText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        return message.toString();
    }

    private synchronized AnthropicChatModel resolve(String currentKey) {
        if (!currentKey.equals(cachedKey)) {
            cachedModel = build(currentKey);
            cachedKey = currentKey;
        }
        return cachedModel;
    }

    private AnthropicChatModel build(String apiKey) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.model())
                .maxTokens(properties.maxTokens())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .maxRetries(2);

        if (properties.temperature() != null) {
            builder.temperature(properties.temperature());
        }
        return builder.build();
    }
}
