package com.studybuddy.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for OpenRouter's OpenAI-compatible chat completions API — gives
 * access to free-tier ({@code :free}-suffixed) models as an alternative to
 * Claude for Tutor/Flashcards/Quiz generation, selectable per session in the
 * Settings UI (see {@code DynamicChatModel}).
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.openrouter")
public record OpenRouterProperties(

        @NotBlank
        String baseUrl,

        @NotBlank
        String model,

        @Positive
        int timeoutSeconds
) {
}
