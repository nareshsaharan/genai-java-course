package com.studybuddy.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for Google's Gemini API (native, not the OpenAI-compat shim) —
 * the same Gemini key can power both chat (an alternative to Claude for
 * Tutor/Flashcards/Quiz) and embeddings (an alternative to OpenAI for
 * document search), selectable independently per session in the Settings UI
 * (see {@code DynamicChatModel} / {@code DynamicEmbeddingModel}).
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.gemini")
public record GeminiProperties(

        @NotBlank
        String chatModel,

        @NotBlank
        String embeddingModel,

        @Positive
        int timeoutSeconds
) {
}
