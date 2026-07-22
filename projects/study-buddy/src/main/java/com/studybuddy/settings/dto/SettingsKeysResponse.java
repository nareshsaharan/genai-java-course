package com.studybuddy.settings.dto;

import com.studybuddy.settings.RuntimeSecretsService.KeyStatus;

/**
 * {@code chatProvider}/{@code embeddingProvider} are the currently-selected
 * provider names (lowercase, e.g. {@code "claude"}, {@code "gemini"}); the
 * five {@code KeyStatus} fields report every provider's key state
 * independently, regardless of which one is currently selected, so the
 * Settings UI can show all key panels at once.
 */
public record SettingsKeysResponse(
        String chatProvider,
        String embeddingProvider,
        KeyStatus claude,
        KeyStatus groq,
        KeyStatus openrouter,
        KeyStatus gemini,
        KeyStatus openai
) {
}
