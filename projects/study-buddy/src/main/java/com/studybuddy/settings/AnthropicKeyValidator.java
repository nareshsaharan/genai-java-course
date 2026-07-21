package com.studybuddy.settings;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ApiKeyValidationException;
import com.studybuddy.config.properties.ClaudeProperties;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Confirms a submitted Anthropic API key actually works before
 * {@link RuntimeSecretsService} persists it. Makes one minimal
 * ({@code maxTokens=1}) real call to Claude using a throwaway client built
 * from the <em>submitted</em> key — never the currently-active one, so a
 * bad submission can never disrupt the app's already-working key.
 */
@Component
public class AnthropicKeyValidator {

    private final Function<String, ChatModel> chatModelFactory;

    public AnthropicKeyValidator(ClaudeProperties properties) {
        this(apiKey -> AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.model())
                .maxTokens(1)
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build());
    }

    /** Package-visible seam for tests — injects a fake client factory instead of a real network call. */
    AnthropicKeyValidator(Function<String, ChatModel> chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    public void validate(String apiKey) {
        try {
            chatModelFactory.apply(apiKey).chat("Hi");
        } catch (LangChain4jException e) {
            throw new ApiKeyValidationException("Anthropic rejected this key: " + e.getMessage());
        }
    }
}
