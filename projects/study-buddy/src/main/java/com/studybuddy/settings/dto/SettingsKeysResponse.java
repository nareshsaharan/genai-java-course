package com.studybuddy.settings.dto;

import com.studybuddy.settings.RuntimeSecretsService.KeyStatus;

public record SettingsKeysResponse(KeyStatus anthropic, KeyStatus openai) {
}
