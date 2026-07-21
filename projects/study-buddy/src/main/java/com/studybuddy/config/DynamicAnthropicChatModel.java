package com.studybuddy.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ClaudeNotConfiguredException;
import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

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
        return resolve().chat(chatRequest);
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    AnthropicChatModel resolveForTest() {
        return resolve();
    }

    private synchronized AnthropicChatModel resolve() {
        String currentKey = secrets.getAnthropicKey();
        if (currentKey == null || currentKey.isBlank()) {
            throw new ClaudeNotConfiguredException(
                    "Claude is not configured — add an Anthropic API key in Settings to enable this.");
        }
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
