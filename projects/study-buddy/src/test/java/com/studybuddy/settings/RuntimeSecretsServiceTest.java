package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.config.properties.ClaudeProperties;

class RuntimeSecretsServiceTest {

    @TempDir
    Path tempDir;

    private ClaudeProperties claudeProperties(String envKey) {
        return new ClaudeProperties(envKey, "claude-sonnet-5", null, 1024, 30);
    }

    private AudioProperties audioProperties(String envKey) {
        return new AudioProperties(envKey, "whisper-1", 10_485_760, 120, 30, 2, false, "./data/audio-recordings", "");
    }

    private RuntimeSecretsService service(String anthropicEnv, String openAiEnv) {
        Path secretsFile = tempDir.resolve("runtime-secrets.properties");
        return new RuntimeSecretsService(claudeProperties(anthropicEnv), audioProperties(openAiEnv), secretsFile);
    }

    @Test
    void noEnvVarAndNoSavedKeyIsUnconfigured() {
        RuntimeSecretsService service = service(null, null);

        assertThat(service.getAnthropicKey()).isNull();
        RuntimeSecretsService.KeyStatus status = service.getAnthropicStatus();
        assertThat(status.configured()).isFalse();
        assertThat(status.source()).isEqualTo("none");
        assertThat(status.maskedKey()).isNull();
    }

    @Test
    void envVarIsUsedAsDefaultWhenNothingSaved() {
        RuntimeSecretsService service = service("sk-ant-envkey12345", null);

        assertThat(service.getAnthropicKey()).isEqualTo("sk-ant-envkey12345");
        RuntimeSecretsService.KeyStatus status = service.getAnthropicStatus();
        assertThat(status.configured()).isTrue();
        assertThat(status.source()).isEqualTo("env");
    }

    @Test
    void savedKeyOverridesEnvVarImmediately() {
        RuntimeSecretsService service = service("sk-ant-envkey12345", null);

        service.setAnthropicKey("sk-ant-uikey6789012");

        assertThat(service.getAnthropicKey()).isEqualTo("sk-ant-uikey6789012");
        assertThat(service.getAnthropicStatus().source()).isEqualTo("saved");
    }

    @Test
    void savedKeyPersistsAcrossServiceRestarts() {
        Path secretsFile = tempDir.resolve("runtime-secrets.properties");
        RuntimeSecretsService first = new RuntimeSecretsService(
                claudeProperties("sk-ant-envkey12345"), audioProperties(null), secretsFile);
        first.setAnthropicKey("sk-ant-uikey6789012");

        RuntimeSecretsService restarted = new RuntimeSecretsService(
                claudeProperties("sk-ant-envkey12345"), audioProperties(null), secretsFile);

        assertThat(restarted.getAnthropicKey()).isEqualTo("sk-ant-uikey6789012");
        assertThat(restarted.getAnthropicStatus().source()).isEqualTo("saved");
    }

    @Test
    void clearingASavedKeyRevertsToEnvVarDefault() {
        RuntimeSecretsService service = service("sk-ant-envkey12345", null);
        service.setAnthropicKey("sk-ant-uikey6789012");

        service.clearAnthropicKey();

        assertThat(service.getAnthropicKey()).isEqualTo("sk-ant-envkey12345");
        assertThat(service.getAnthropicStatus().source()).isEqualTo("env");
    }

    @Test
    void clearingWithNoEnvVarRevertsToUnconfigured() {
        RuntimeSecretsService service = service(null, null);
        service.setAnthropicKey("sk-ant-uikey6789012");

        service.clearAnthropicKey();

        assertThat(service.getAnthropicStatus().configured()).isFalse();
    }

    @Test
    void maskedKeyShowsOnlyPrefixAndLastFourCharacters() {
        RuntimeSecretsService service = service("sk-ant-api03-1234567890abcdef", null);

        String masked = service.getAnthropicStatus().maskedKey();

        assertThat(masked).startsWith("sk-ant");
        assertThat(masked).endsWith("cdef");
        assertThat(masked).doesNotContain("1234567890abcdef");
    }

    @Test
    void anthropicAndOpenAiKeysAreIndependent() {
        RuntimeSecretsService service = service("sk-ant-envkey12345", "sk-openai-envkey123");

        service.setAnthropicKey("sk-ant-uikey6789012");

        assertThat(service.getAnthropicStatus().source()).isEqualTo("saved");
        assertThat(service.getOpenAiStatus().source()).isEqualTo("env");
        assertThat(service.getOpenAiKey()).isEqualTo("sk-openai-envkey123");
    }

    @Test
    void savingOnlyOneKeyDoesNotPersistTheOthersEnvValueToFile() throws Exception {
        Path secretsFile = tempDir.resolve("runtime-secrets.properties");
        RuntimeSecretsService service = new RuntimeSecretsService(
                claudeProperties("sk-ant-envkey12345"), audioProperties("sk-openai-envkey123"), secretsFile);

        service.setAnthropicKey("sk-ant-uikey6789012");

        Properties onDisk = new Properties();
        try (var in = Files.newInputStream(secretsFile)) {
            onDisk.load(in);
        }
        assertThat(onDisk.getProperty("anthropic.apiKey")).isEqualTo("sk-ant-uikey6789012");
        assertThat(onDisk.getProperty("openai.apiKey")).isNull();
    }
}
