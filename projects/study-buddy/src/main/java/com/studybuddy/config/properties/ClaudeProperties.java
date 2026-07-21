package com.studybuddy.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the Anthropic (Claude) chat model used by tutor chat,
 * flashcard generation and quiz generation. {@code apiKey} seeds
 * {@link com.studybuddy.settings.RuntimeSecretsService} at startup — it is
 * deliberately NOT {@code @NotBlank}, matching {@code AudioProperties}:
 * the app must still start with no key configured anywhere, since a key can
 * now be added later from the Settings UI without a restart. Requests that
 * need Claude before any key is configured fail with a clear 503
 * (see {@code DynamicAnthropicChatModel}), not an application startup crash.
 *
 * <p>{@code temperature} is intentionally a nullable {@link Double}, not a
 * primitive: some Claude models (confirmed against a live call to
 * claude-sonnet-5) reject the request entirely with
 * {@code "temperature is deprecated for this model"} if any value is sent
 * at all. Leaving {@code ANTHROPIC_TEMPERATURE} unset means "don't send the
 * parameter" — see {@code DynamicAnthropicChatModel}, which only calls
 * {@code .temperature(...)} when this is non-null.
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.claude")
public record ClaudeProperties(

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
