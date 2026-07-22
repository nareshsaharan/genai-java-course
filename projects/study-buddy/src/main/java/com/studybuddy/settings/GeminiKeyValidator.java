package com.studybuddy.settings;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ApiKeyValidationException;
import com.studybuddy.config.properties.GeminiProperties;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

/**
 * Confirms a submitted Gemini API key actually works before
 * {@link RuntimeSecretsService} persists it. Makes one minimal
 * ({@code maxOutputTokens=1}) real call to Gemini using a throwaway client
 * built from the <em>submitted</em> key — never the currently-active one, so
 * a bad submission can never disrupt the app's already-working key.
 */
@Component
public class GeminiKeyValidator {

    private final Function<String, ChatModel> chatModelFactory;

    @Autowired
    public GeminiKeyValidator(GeminiProperties properties) {
        this(apiKey -> GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.chatModel())
                .maxOutputTokens(1)
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build());
    }

    /** Package-visible seam for tests — injects a fake client factory instead of a real network call. */
    GeminiKeyValidator(Function<String, ChatModel> chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    public void validate(String apiKey) {
        try {
            chatModelFactory.apply(apiKey).chat("Hi");
        } catch (LangChain4jException e) {
            throw new ApiKeyValidationException("Gemini rejected this key: " + e.getMessage());
        }
    }
}
