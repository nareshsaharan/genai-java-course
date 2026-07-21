package com.studybuddy.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.config.properties.ClaudeProperties;

/**
 * Single source of truth for the currently-active Claude and OpenAI API
 * keys. Seeded at startup from environment variables (via
 * {@link ClaudeProperties#apiKey()} / {@link AudioProperties#apiKey()});
 * overridable at runtime through the Settings UI without an app restart.
 * Every override is persisted to a local, gitignored properties file so it
 * survives a restart — precedence on load is: persisted file &gt; env var
 * &gt; unconfigured. Never logs a raw key.
 */
@Component
public class RuntimeSecretsService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSecretsService.class);
    private static final String ANTHROPIC_PROPERTY = "anthropic.apiKey";
    private static final String OPENAI_PROPERTY = "openai.apiKey";
    private static final Path DEFAULT_SECRETS_FILE = Path.of("./data/runtime-secrets.properties");

    private final Path secretsFilePath;
    private final String anthropicEnvDefault;
    private final String openAiEnvDefault;

    private final AtomicReference<String> anthropicKey = new AtomicReference<>();
    private final AtomicReference<String> openAiKey = new AtomicReference<>();
    private final AtomicBoolean anthropicFromFile = new AtomicBoolean(false);
    private final AtomicBoolean openAiFromFile = new AtomicBoolean(false);

    public RuntimeSecretsService(ClaudeProperties claudeProperties, AudioProperties audioProperties) {
        this(claudeProperties, audioProperties, DEFAULT_SECRETS_FILE);
    }

    RuntimeSecretsService(ClaudeProperties claudeProperties, AudioProperties audioProperties, Path secretsFilePath) {
        this.secretsFilePath = secretsFilePath;
        this.anthropicEnvDefault = claudeProperties.apiKey();
        this.openAiEnvDefault = audioProperties.apiKey();
        loadFromFileOrEnv();
    }

    private void loadFromFileOrEnv() {
        Properties saved = readPersistedFile();
        String savedAnthropic = saved.getProperty(ANTHROPIC_PROPERTY);
        String savedOpenAi = saved.getProperty(OPENAI_PROPERTY);

        if (StringUtils.hasText(savedAnthropic)) {
            anthropicKey.set(savedAnthropic);
            anthropicFromFile.set(true);
        } else {
            anthropicKey.set(anthropicEnvDefault);
        }

        if (StringUtils.hasText(savedOpenAi)) {
            openAiKey.set(savedOpenAi);
            openAiFromFile.set(true);
        } else {
            openAiKey.set(openAiEnvDefault);
        }
    }

    public String getAnthropicKey() {
        return anthropicKey.get();
    }

    public String getOpenAiKey() {
        return openAiKey.get();
    }

    public KeyStatus getAnthropicStatus() {
        return status(anthropicKey.get(), anthropicFromFile.get());
    }

    public KeyStatus getOpenAiStatus() {
        return status(openAiKey.get(), openAiFromFile.get());
    }

    public void setAnthropicKey(String newKey) {
        anthropicKey.set(newKey);
        anthropicFromFile.set(true);
        persist();
    }

    public void setOpenAiKey(String newKey) {
        openAiKey.set(newKey);
        openAiFromFile.set(true);
        persist();
    }

    public void clearAnthropicKey() {
        anthropicKey.set(anthropicEnvDefault);
        anthropicFromFile.set(false);
        persist();
    }

    public void clearOpenAiKey() {
        openAiKey.set(openAiEnvDefault);
        openAiFromFile.set(false);
        persist();
    }

    private static KeyStatus status(String key, boolean fromFile) {
        if (!StringUtils.hasText(key)) {
            return new KeyStatus(false, "none", null);
        }
        return new KeyStatus(true, fromFile ? "saved" : "env", mask(key));
    }

    static String mask(String key) {
        if (key.length() <= 10) {
            return "***";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    private Properties readPersistedFile() {
        Properties properties = new Properties();
        if (!Files.exists(secretsFilePath)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(secretsFilePath)) {
            properties.load(in);
        } catch (IOException e) {
            log.warn("Failed to read persisted runtime secrets file at {}", secretsFilePath, e);
        }
        return properties;
    }

    private void persist() {
        Properties properties = new Properties();
        if (anthropicFromFile.get() && StringUtils.hasText(anthropicKey.get())) {
            properties.setProperty(ANTHROPIC_PROPERTY, anthropicKey.get());
        }
        if (openAiFromFile.get() && StringUtils.hasText(openAiKey.get())) {
            properties.setProperty(OPENAI_PROPERTY, openAiKey.get());
        }
        try {
            Path parent = secretsFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(secretsFilePath)) {
                properties.store(out, "Study Buddy runtime-saved API keys — do not commit");
            }
        } catch (IOException e) {
            log.warn("Failed to persist runtime secrets file at {}", secretsFilePath, e);
        }
    }

    /** API-facing view of one provider's key state — never the raw key itself. */
    public record KeyStatus(boolean configured, String source, String maskedKey) {
    }
}
