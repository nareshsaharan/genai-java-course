package com.studybuddy.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.settings.dto.SaveKeyRequest;
import com.studybuddy.settings.dto.SettingsKeysResponse;

import jakarta.validation.Valid;

/**
 * Exposes the current Claude/OpenAI key status and allows saving or clearing
 * each provider's runtime-configurable key. All real logic is delegated to
 * {@link RuntimeSecretsService} (persistence) and the per-provider validators
 * (live check against the real provider before a submitted key is trusted).
 */
@RestController
@RequestMapping("/api/settings/keys")
public class SettingsController {

    private final RuntimeSecretsService secrets;
    private final AnthropicKeyValidator anthropicKeyValidator;
    private final OpenAiKeyValidator openAiKeyValidator;

    public SettingsController(
            RuntimeSecretsService secrets,
            AnthropicKeyValidator anthropicKeyValidator,
            OpenAiKeyValidator openAiKeyValidator) {
        this.secrets = secrets;
        this.anthropicKeyValidator = anthropicKeyValidator;
        this.openAiKeyValidator = openAiKeyValidator;
    }

    @GetMapping
    public SettingsKeysResponse getStatus() {
        return new SettingsKeysResponse(secrets.getAnthropicStatus(), secrets.getOpenAiStatus());
    }

    @PutMapping("/anthropic")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> saveAnthropicKey(@Valid @RequestBody SaveKeyRequest request) {
        anthropicKeyValidator.validate(request.apiKey());
        secrets.setAnthropicKey(request.apiKey());
        return ResponseEntity.ok(secrets.getAnthropicStatus());
    }

    @PutMapping("/openai")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> saveOpenAiKey(@Valid @RequestBody SaveKeyRequest request) {
        openAiKeyValidator.validate(request.apiKey());
        secrets.setOpenAiKey(request.apiKey());
        return ResponseEntity.ok(secrets.getOpenAiStatus());
    }

    @DeleteMapping("/anthropic")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> clearAnthropicKey() {
        secrets.clearAnthropicKey();
        return ResponseEntity.ok(secrets.getAnthropicStatus());
    }

    @DeleteMapping("/openai")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> clearOpenAiKey() {
        secrets.clearOpenAiKey();
        return ResponseEntity.ok(secrets.getOpenAiStatus());
    }
}
