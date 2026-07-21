package com.studybuddy.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the Anthropic (Claude) chat model used by tutor chat,
 * flashcard generation and quiz generation. apiKey is read from the
 * ANTHROPIC_API_KEY environment variable — never hardcoded.
 *
 * <p>{@code temperature} is intentionally a nullable {@link Double}, not a
 * primitive: some Claude models (confirmed against a live call to
 * claude-sonnet-5) reject the request entirely with
 * {@code "temperature is deprecated for this model"} if any value is sent
 * at all. Leaving {@code ANTHROPIC_TEMPERATURE} unset means "don't send the
 * parameter" — see {@code ChatModelConfig}, which only calls
 * {@code .temperature(...)} when this is non-null.
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.claude")
public record ClaudeProperties(

        @NotBlank
        String apiKey,

        @NotBlank
        String model,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double temperature,

        @Positive
        int maxTokens,

        @Positive
        int timeoutSeconds
) {
}
