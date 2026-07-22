package com.studybuddy.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.common.exception.UnknownProviderException;
import com.studybuddy.settings.dto.SaveKeyRequest;
import com.studybuddy.settings.dto.SelectProviderRequest;
import com.studybuddy.settings.dto.SettingsKeysResponse;

import jakarta.validation.Valid;

/**
 * Exposes every provider's key status and lets the Settings UI save/clear
 * any of the five provider keys (claude, groq, openrouter, gemini, openai)
 * and switch which provider is currently selected for chat and for
 * embeddings — independently of each other. All real logic is delegated to
 * {@link RuntimeSecretsService} (per-session state) and the per-provider
 * validators (live check against the real provider before a submitted key
 * is trusted).
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final RuntimeSecretsService secrets;
    private final AnthropicKeyValidator anthropicKeyValidator;
    private final GroqKeyValidator groqKeyValidator;
    private final OpenRouterKeyValidator openRouterKeyValidator;
    private final GeminiKeyValidator geminiKeyValidator;
    private final OpenAiKeyValidator openAiKeyValidator;

    public SettingsController(
            RuntimeSecretsService secrets,
            AnthropicKeyValidator anthropicKeyValidator,
            GroqKeyValidator groqKeyValidator,
            OpenRouterKeyValidator openRouterKeyValidator,
            GeminiKeyValidator geminiKeyValidator,
            OpenAiKeyValidator openAiKeyValidator) {
        this.secrets = secrets;
        this.anthropicKeyValidator = anthropicKeyValidator;
        this.groqKeyValidator = groqKeyValidator;
        this.openRouterKeyValidator = openRouterKeyValidator;
        this.geminiKeyValidator = geminiKeyValidator;
        this.openAiKeyValidator = openAiKeyValidator;
    }

    @GetMapping("/keys")
    public SettingsKeysResponse getStatus() {
        return new SettingsKeysResponse(
                secrets.getChatProvider().name().toLowerCase(),
                secrets.getEmbeddingProvider().name().toLowerCase(),
                secrets.getClaudeStatus(),
                secrets.getGroqStatus(),
                secrets.getOpenRouterStatus(),
                secrets.getGeminiStatus(),
                secrets.getOpenAiStatus());
    }

    @PutMapping("/keys/{provider}")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> saveKey(
            @PathVariable String provider, @Valid @RequestBody SaveKeyRequest request) {
        String apiKey = request.apiKey();
        return switch (provider.toLowerCase()) {
            case "claude" -> {
                anthropicKeyValidator.validate(apiKey);
                secrets.setClaudeKey(apiKey);
                yield ResponseEntity.ok(secrets.getClaudeStatus());
            }
            case "groq" -> {
                groqKeyValidator.validate(apiKey);
                secrets.setGroqKey(apiKey);
                yield ResponseEntity.ok(secrets.getGroqStatus());
            }
            case "openrouter" -> {
                openRouterKeyValidator.validate(apiKey);
                secrets.setOpenRouterKey(apiKey);
                yield ResponseEntity.ok(secrets.getOpenRouterStatus());
            }
            case "gemini" -> {
                geminiKeyValidator.validate(apiKey);
                secrets.setGeminiKey(apiKey);
                yield ResponseEntity.ok(secrets.getGeminiStatus());
            }
            case "openai" -> {
                openAiKeyValidator.validate(apiKey);
                secrets.setOpenAiKey(apiKey);
                yield ResponseEntity.ok(secrets.getOpenAiStatus());
            }
            default -> throw new UnknownProviderException("Unknown provider: " + provider);
        };
    }

    @DeleteMapping("/keys/{provider}")
    public ResponseEntity<RuntimeSecretsService.KeyStatus> clearKey(@PathVariable String provider) {
        return switch (provider.toLowerCase()) {
            case "claude" -> {
                secrets.clearClaudeKey();
                yield ResponseEntity.ok(secrets.getClaudeStatus());
            }
            case "groq" -> {
                secrets.clearGroqKey();
                yield ResponseEntity.ok(secrets.getGroqStatus());
            }
            case "openrouter" -> {
                secrets.clearOpenRouterKey();
                yield ResponseEntity.ok(secrets.getOpenRouterStatus());
            }
            case "gemini" -> {
                secrets.clearGeminiKey();
                yield ResponseEntity.ok(secrets.getGeminiStatus());
            }
            case "openai" -> {
                secrets.clearOpenAiKey();
                yield ResponseEntity.ok(secrets.getOpenAiStatus());
            }
            default -> throw new UnknownProviderException("Unknown provider: " + provider);
        };
    }

    @PutMapping("/chat-provider")
    public SettingsKeysResponse setChatProvider(@Valid @RequestBody SelectProviderRequest request) {
        secrets.setChatProvider(parseEnum(ChatProvider.class, request.provider()));
        return getStatus();
    }

    @PutMapping("/embedding-provider")
    public SettingsKeysResponse setEmbeddingProvider(@Valid @RequestBody SelectProviderRequest request) {
        secrets.setEmbeddingProvider(parseEnum(EmbeddingProvider.class, request.provider()));
        return getStatus();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        try {
            return Enum.valueOf(enumType, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnknownProviderException("Unknown provider: " + value);
        }
    }
}
