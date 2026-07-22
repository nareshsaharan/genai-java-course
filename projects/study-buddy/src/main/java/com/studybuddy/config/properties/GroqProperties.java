package com.studybuddy.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for Groq's OpenAI-compatible chat completions API — a free/cheap
 * alternative to Claude for Tutor/Flashcards/Quiz generation, selectable per
 * session in the Settings UI (see {@code DynamicChatModel}).
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.groq")
public record GroqProperties(

        @NotBlank
        String baseUrl,

        @NotBlank
        String model,

        @Positive
        int timeoutSeconds
) {
}
