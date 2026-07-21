# UI Redesign + Runtime-Configurable API Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Study Buddy's Claude and OpenAI API keys be entered, verified, and stored from the browser UI instead of only environment variables, and redesign the frontend into a tabbed, two-theme (light Indigo Educational / dark Slate Dark-First) interface.

**Architecture:** Backend: a new `RuntimeSecretsService` becomes the single source of truth for the currently-active Claude/OpenAI keys (seeded from env vars, overridable at runtime, persisted to a gitignored local file); a `DynamicAnthropicChatModel` wrapper replaces the old fixed `ChatModelConfig` bean so the Claude client can be rebuilt when the key changes without an app restart; a new `SettingsController` exposes get/save/clear endpoints, validating a submitted key against the real provider (using a throwaway client) before ever persisting it. Frontend: the existing single-scroll page becomes tab panels (same DOM ids, so `app.js`'s existing business logic is untouched), styled with two distinct CSS custom-property palettes switched via `data-theme` on `<html>`, plus a new Settings tab and gating banners on Tutor/Flashcards/Quiz/Voice when a required key isn't configured.

**Tech Stack:** Java 21, Spring Boot 3.5.16, LangChain4j 1.17.2 (`AnthropicChatModel`), JUnit 5 + Mockito (backend tests — no test ever calls a real Anthropic/OpenAI endpoint), vanilla HTML/CSS/JS (no test framework for the frontend in this project — verified via `node --check` for syntax and a live browser check, matching this project's existing convention for every prior frontend change).

Spec: `docs/superpowers/specs/2026-07-21-ui-redesign-and-runtime-api-keys-design.md`

---

## Part 1 — Backend: runtime-configurable API keys

### Task 1: `RuntimeSecretsService`

**Files:**
- Create: `src/main/java/com/studybuddy/settings/RuntimeSecretsService.java`
- Test: `src/test/java/com/studybuddy/settings/RuntimeSecretsServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/nareshsaharan/Desktop/genai-java-course/projects/study-buddy && mvn test -Dtest=RuntimeSecretsServiceTest`
Expected: FAIL — compilation error, `RuntimeSecretsService` does not exist.

- [ ] **Step 3: Write the implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=RuntimeSecretsServiceTest`
Expected: PASS — 10 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/studybuddy/settings/RuntimeSecretsService.java src/test/java/com/studybuddy/settings/RuntimeSecretsServiceTest.java
git commit -m "feat: add RuntimeSecretsService for runtime-configurable API keys"
```

---

### Task 2: Relax `ClaudeProperties.apiKey` from `@NotBlank`

**Files:**
- Modify: `src/main/java/com/studybuddy/config/properties/ClaudeProperties.java`

- [ ] **Step 1: Update the record**

Replace the full file content:

```java
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
```

- [ ] **Step 2: Verify the app still compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/studybuddy/config/properties/ClaudeProperties.java
git commit -m "feat: allow Study Buddy to start without ANTHROPIC_API_KEY set"
```

---

### Task 3: New exceptions + `GlobalExceptionHandler` wiring

**Files:**
- Create: `src/main/java/com/studybuddy/common/exception/ClaudeNotConfiguredException.java`
- Create: `src/main/java/com/studybuddy/common/exception/ApiKeyValidationException.java`
- Modify: `src/main/java/com/studybuddy/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/studybuddy/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test additions**

Add these two test methods inside the existing `GlobalExceptionHandlerTest` class (anywhere among the other `@Test` methods, e.g. right after `unsupportedContentTypeOnAMultipartEndpointMapsTo415NotInternalServerError`):

```java
    @Test
    void claudeNotConfiguredMapsTo503WithConsistentSchema() {
        ProblemDetail problem = handler.handleClaudeNotConfigured(
                new ClaudeNotConfiguredException("Claude is not configured"));
        assertConsistentSchema(problem, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void apiKeyValidationFailureMapsTo422WithConsistentSchema() {
        ProblemDetail problem = handler.handleApiKeyValidation(
                new ApiKeyValidationException("Anthropic rejected this key"));
        assertConsistentSchema(problem, HttpStatus.UNPROCESSABLE_ENTITY);
    }
```

No new imports are needed — `ClaudeNotConfiguredException` and `ApiKeyValidationException` live in the same `com.studybuddy.common.exception` package as the test class, so they're directly referenceable.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — compilation error, `ClaudeNotConfiguredException`/`ApiKeyValidationException`/`handleClaudeNotConfigured`/`handleApiKeyValidation` do not exist.

- [ ] **Step 3: Create the exception classes**

`src/main/java/com/studybuddy/common/exception/ClaudeNotConfiguredException.java`:

```java
package com.studybuddy.common.exception;

/** Thrown when a Claude call is attempted but no Anthropic API key is configured (env var or Settings). */
public class ClaudeNotConfiguredException extends RuntimeException {

    public ClaudeNotConfiguredException(String message) {
        super(message);
    }
}
```

`src/main/java/com/studybuddy/common/exception/ApiKeyValidationException.java`:

```java
package com.studybuddy.common.exception;

/** Thrown when a submitted API key fails verification against the real provider before being saved. */
public class ApiKeyValidationException extends RuntimeException {

    public ApiKeyValidationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Add handlers to `GlobalExceptionHandler`**

In `src/main/java/com/studybuddy/common/exception/GlobalExceptionHandler.java`, insert these two handler methods immediately after `handleAudioServiceNotConfigured` (right before the `handleUnsupportedMediaType` method):

```java
    @ExceptionHandler(ClaudeNotConfiguredException.class)
    public ProblemDetail handleClaudeNotConfigured(ClaudeNotConfiguredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(ApiKeyValidationException.class)
    public ProblemDetail handleApiKeyValidation(ApiKeyValidationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS — all tests green (2 new + existing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/studybuddy/common/exception/ClaudeNotConfiguredException.java \
        src/main/java/com/studybuddy/common/exception/ApiKeyValidationException.java \
        src/main/java/com/studybuddy/common/exception/GlobalExceptionHandler.java \
        src/test/java/com/studybuddy/common/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add ClaudeNotConfiguredException (503) and ApiKeyValidationException (422)"
```

---

### Task 4: `DynamicAnthropicChatModel` (replaces `ChatModelConfig`)

**Files:**
- Create: `src/main/java/com/studybuddy/config/DynamicAnthropicChatModel.java`
- Delete: `src/main/java/com/studybuddy/config/ChatModelConfig.java`
- Test: `src/test/java/com/studybuddy/config/DynamicAnthropicChatModelTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.studybuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ClaudeNotConfiguredException;
import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

class DynamicAnthropicChatModelTest {

    private ClaudeProperties properties() {
        return new ClaudeProperties(null, "claude-sonnet-5", null, 1024, 30);
    }

    @Test
    void throwsClaudeNotConfiguredWhenNoKeyIsSet() {
        RuntimeSecretsService secrets = mockSecrets(null);
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        assertThatThrownBy(() -> model.doChat(request()))
                .isInstanceOf(ClaudeNotConfiguredException.class);
    }

    @Test
    void reusesCachedClientWhenKeyHasNotChanged() {
        RuntimeSecretsService secrets = mockSecrets("sk-ant-test-key-aaaa");
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        var first = model.resolveForTest();
        var second = model.resolveForTest();

        assertThat(first).isSameAs(second);
    }

    @Test
    void rebuildsClientWhenKeyChanges() {
        java.util.concurrent.atomic.AtomicReference<String> key =
                new java.util.concurrent.atomic.AtomicReference<>("sk-ant-test-key-aaaa");
        RuntimeSecretsService secrets = org.mockito.Mockito.mock(RuntimeSecretsService.class);
        org.mockito.Mockito.when(secrets.getAnthropicKey()).thenAnswer(invocation -> key.get());
        DynamicAnthropicChatModel model = new DynamicAnthropicChatModel(secrets, properties());

        var first = model.resolveForTest();
        key.set("sk-ant-test-key-bbbb");
        var second = model.resolveForTest();

        assertThat(first).isNotSameAs(second);
    }

    private static RuntimeSecretsService mockSecrets(String key) {
        RuntimeSecretsService secrets = org.mockito.Mockito.mock(RuntimeSecretsService.class);
        org.mockito.Mockito.when(secrets.getAnthropicKey()).thenReturn(key);
        return secrets;
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("test")).build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DynamicAnthropicChatModelTest`
Expected: FAIL — compilation error, `DynamicAnthropicChatModel` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.studybuddy.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ClaudeNotConfiguredException;
import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * {@link ChatModel} implementation that reads the currently-active Anthropic
 * API key from {@link RuntimeSecretsService} on every call, instead of
 * having a key baked in permanently at application startup. Caches a real
 * {@link AnthropicChatModel} keyed by the current key value, rebuilding only
 * when the key actually changes (e.g. after a Settings save) — so a normal
 * request pays no extra cost beyond the first one after a key change.
 * Replaces the old fixed {@code ChatModelConfig} bean; {@code TutorAssistant}
 * / {@code FlashcardGenerator} / {@code QuizGenerator} are unaffected since
 * they only depend on the {@link ChatModel} interface.
 */
@Component
public class DynamicAnthropicChatModel implements ChatModel {

    private final RuntimeSecretsService secrets;
    private final ClaudeProperties properties;

    private volatile String cachedKey;
    private volatile AnthropicChatModel cachedModel;

    public DynamicAnthropicChatModel(RuntimeSecretsService secrets, ClaudeProperties properties) {
        this.secrets = secrets;
        this.properties = properties;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return resolve().chat(chatRequest);
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    AnthropicChatModel resolveForTest() {
        return resolve();
    }

    private synchronized AnthropicChatModel resolve() {
        String currentKey = secrets.getAnthropicKey();
        if (currentKey == null || currentKey.isBlank()) {
            throw new ClaudeNotConfiguredException(
                    "Claude is not configured — add an Anthropic API key in Settings to enable this.");
        }
        if (!currentKey.equals(cachedKey)) {
            cachedModel = build(currentKey);
            cachedKey = currentKey;
        }
        return cachedModel;
    }

    private AnthropicChatModel build(String apiKey) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.model())
                .maxTokens(properties.maxTokens())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .maxRetries(2);

        if (properties.temperature() != null) {
            builder.temperature(properties.temperature());
        }
        return builder.build();
    }
}
```

- [ ] **Step 4: Delete the now-superseded `ChatModelConfig`**

```bash
rm src/main/java/com/studybuddy/config/ChatModelConfig.java
```

(`DynamicAnthropicChatModel` is itself a `@Component implementing ChatModel` — leaving the old `@Bean public ChatModel chatModel(...)` method in place would create two competing `ChatModel` beans and fail Spring's context startup with an ambiguous-bean error.)

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=DynamicAnthropicChatModelTest`
Expected: PASS — 3 tests green, no real network call made (only `doChat` on a mocked-key-absent case throws before touching the network; the cache-identity tests never call `.chat()`, only `resolveForTest()`).

- [ ] **Step 6: Run the full test suite to confirm nothing else broke**

Run: `mvn test`
Expected: Same baseline as before this change (0 failures among the runnable tests; only the pre-existing Testcontainers/Docker-unavailable errors, if Docker isn't available in your environment).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/studybuddy/config/DynamicAnthropicChatModel.java \
        src/test/java/com/studybuddy/config/DynamicAnthropicChatModelTest.java
git rm src/main/java/com/studybuddy/config/ChatModelConfig.java
git commit -m "feat: replace fixed ChatModel bean with a runtime-key-aware wrapper"
```

---

### Task 5: `AnthropicKeyValidator`

**Files:**
- Create: `src/main/java/com/studybuddy/settings/AnthropicKeyValidator.java`
- Test: `src/test/java/com/studybuddy/settings/AnthropicKeyValidatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.ChatModel;

class AnthropicKeyValidatorTest {

    @Test
    void acceptsAKeyWhenTheThrowawayModelRespondsSuccessfully() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> throwawayModel);

        assertThatCode(() -> validator.validate("sk-ant-a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void wrapsAnAuthenticationFailureAsApiKeyValidationException() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenThrow(new AuthenticationException("invalid x-api-key"));
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> throwawayModel);

        assertThatThrownBy(() -> validator.validate("sk-ant-bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("invalid x-api-key");
    }

    @Test
    void buildsTheThrowawayModelWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return throwawayModel;
        });

        validator.validate("sk-ant-the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("sk-ant-the-submitted-key");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AnthropicKeyValidatorTest`
Expected: FAIL — compilation error, `AnthropicKeyValidator` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.studybuddy.settings;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ApiKeyValidationException;
import com.studybuddy.config.properties.ClaudeProperties;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Confirms a submitted Anthropic API key actually works before
 * {@link RuntimeSecretsService} persists it. Makes one minimal
 * ({@code maxTokens=1}) real call to Claude using a throwaway client built
 * from the <em>submitted</em> key — never the currently-active one, so a
 * bad submission can never disrupt the app's already-working key.
 */
@Component
public class AnthropicKeyValidator {

    private final Function<String, ChatModel> chatModelFactory;

    public AnthropicKeyValidator(ClaudeProperties properties) {
        this(apiKey -> AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.model())
                .maxTokens(1)
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build());
    }

    /** Package-visible seam for tests — injects a fake client factory instead of a real network call. */
    AnthropicKeyValidator(Function<String, ChatModel> chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    public void validate(String apiKey) {
        try {
            chatModelFactory.apply(apiKey).chat("Hi");
        } catch (LangChain4jException e) {
            throw new ApiKeyValidationException("Anthropic rejected this key: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AnthropicKeyValidatorTest`
Expected: PASS — 3 tests green, no real network call made (the factory is entirely mocked).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/studybuddy/settings/AnthropicKeyValidator.java \
        src/test/java/com/studybuddy/settings/AnthropicKeyValidatorTest.java
git commit -m "feat: add AnthropicKeyValidator for pre-save key verification"
```

---

### Task 6: `OpenAiKeyValidator`

**Files:**
- Create: `src/main/java/com/studybuddy/settings/OpenAiKeyValidator.java`
- Test: `src/test/java/com/studybuddy/settings/OpenAiKeyValidatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

class OpenAiKeyValidatorTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> responseWith(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void acceptsAKeyWhenTheModelsEndpointReturns200() {
        OpenAiKeyValidator validator = new OpenAiKeyValidator(apiKey -> responseWith(200, "{\"data\":[]}"));

        assertThatCode(() -> validator.validate("sk-a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAKeyWhenTheModelsEndpointReturns401() {
        OpenAiKeyValidator validator = new OpenAiKeyValidator(
                apiKey -> responseWith(401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        assertThatThrownBy(() -> validator.validate("sk-bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Incorrect API key provided");
    }

    @Test
    void buildsTheProbeWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        OpenAiKeyValidator validator = new OpenAiKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return responseWith(200, "{}");
        });

        validator.validate("sk-the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("sk-the-submitted-key");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=OpenAiKeyValidatorTest`
Expected: FAIL — compilation error, `OpenAiKeyValidator` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.studybuddy.settings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ApiKeyValidationException;

/**
 * Confirms a submitted OpenAI API key actually works before
 * {@link RuntimeSecretsService} persists it. Calls the cheap
 * {@code GET /v1/models} endpoint with the <em>submitted</em> key — enough
 * to prove authentication works without consuming any Whisper transcription
 * minutes.
 */
@Component
public class OpenAiKeyValidator {

    private static final String MODELS_URL = "https://api.openai.com/v1/models";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Function<String, HttpResponse<String>> prober;

    public OpenAiKeyValidator() {
        this(OpenAiKeyValidator::probeRealEndpoint);
    }

    /** Package-visible seam for tests — injects a fake prober instead of a real network call. */
    OpenAiKeyValidator(Function<String, HttpResponse<String>> prober) {
        this.prober = prober;
    }

    public void validate(String apiKey) {
        HttpResponse<String> response = prober.apply(apiKey);
        if (response.statusCode() != 200) {
            throw new ApiKeyValidationException(
                    "OpenAI rejected this key (HTTP " + response.statusCode() + "): " + truncate(response.body()));
        }
    }

    private static HttpResponse<String> probeRealEndpoint(String apiKey) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_URL))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ApiKeyValidationException("Could not reach OpenAI to verify this key: " + e.getMessage());
        }
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=OpenAiKeyValidatorTest`
Expected: PASS — 3 tests green, no real network call made.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/studybuddy/settings/OpenAiKeyValidator.java \
        src/test/java/com/studybuddy/settings/OpenAiKeyValidatorTest.java
git commit -m "feat: add OpenAiKeyValidator for pre-save key verification"
```

---

### Task 7: Point `OpenAiWhisperClient` at `RuntimeSecretsService`

**Files:**
- Modify: `src/main/java/com/studybuddy/audio/client/OpenAiWhisperClient.java`
- Test: `src/test/java/com/studybuddy/audio/client/OpenAiWhisperClientTest.java` (new — no dedicated test existed for this class before)

- [ ] **Step 1: Write the failing test**

```java
package com.studybuddy.audio.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studybuddy.common.exception.AudioServiceNotConfiguredException;
import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.settings.RuntimeSecretsService;

class OpenAiWhisperClientTest {

    private AudioProperties properties() {
        return new AudioProperties(null, "whisper-1", 10_485_760, 120, 30, 2, false, "./data/audio-recordings", "");
    }

    @Test
    void throwsAudioServiceNotConfiguredWhenRuntimeSecretsHasNoOpenAiKey() {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn(null);
        OpenAiWhisperClient client = new OpenAiWhisperClient(properties(), new ObjectMapper(), secrets);

        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2, 3}, "question.wav", "audio/wav"))
                .isInstanceOf(AudioServiceNotConfiguredException.class);
    }

    @Test
    void throwsAudioServiceNotConfiguredWhenRuntimeSecretsHasBlankOpenAiKey() {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn("   ");
        OpenAiWhisperClient client = new OpenAiWhisperClient(properties(), new ObjectMapper(), secrets);

        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2, 3}, "question.wav", "audio/wav"))
                .isInstanceOf(AudioServiceNotConfiguredException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=OpenAiWhisperClientTest`
Expected: FAIL — compilation error, no `OpenAiWhisperClient(AudioProperties, ObjectMapper, RuntimeSecretsService)` constructor exists yet.

- [ ] **Step 3: Update the implementation**

In `src/main/java/com/studybuddy/audio/client/OpenAiWhisperClient.java`:

Replace the import block's `com.studybuddy.config.properties.AudioProperties` line by adding one more import right after it:

```java
import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.settings.RuntimeSecretsService;
```

Replace the field declarations and constructor:

```java
    private final AudioProperties properties;
    private final RuntimeSecretsService secrets;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiWhisperClient(AudioProperties properties, ObjectMapper objectMapper, RuntimeSecretsService secrets) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.secrets = secrets;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
    }
```

Replace the two `properties.apiKey()` usages:

In `transcribe(...)`, replace:
```java
        if (!StringUtils.hasText(properties.apiKey())) {
```
with:
```java
        if (!StringUtils.hasText(secrets.getOpenAiKey())) {
```

In `doTranscribe(...)`, replace:
```java
                .header("Authorization", "Bearer " + properties.apiKey())
```
with:
```java
                .header("Authorization", "Bearer " + secrets.getOpenAiKey())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=OpenAiWhisperClientTest`
Expected: PASS — 2 tests green.

- [ ] **Step 5: Run the full test suite to confirm nothing else broke**

Run: `mvn test`
Expected: Same baseline as before (0 failures among runnable tests — `AudioTranscriptionServiceTest` mocks `WhisperClient` directly and is unaffected by this constructor change).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/studybuddy/audio/client/OpenAiWhisperClient.java \
        src/test/java/com/studybuddy/audio/client/OpenAiWhisperClientTest.java
git commit -m "feat: read the OpenAI key from RuntimeSecretsService instead of AudioProperties"
```

---

### Task 8: `SettingsController` + DTOs

**Files:**
- Create: `src/main/java/com/studybuddy/settings/dto/SaveKeyRequest.java`
- Create: `src/main/java/com/studybuddy/settings/dto/SettingsKeysResponse.java`
- Create: `src/main/java/com/studybuddy/settings/SettingsController.java`
- Test: `src/test/java/com/studybuddy/settings/SettingsControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.studybuddy.settings;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.common.exception.ApiKeyValidationException;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeSecretsService secrets;

    @MockitoBean
    private AnthropicKeyValidator anthropicKeyValidator;

    @MockitoBean
    private OpenAiKeyValidator openAiKeyValidator;

    @Test
    void getStatusReturnsBothProvidersCurrentState() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "env", "sk-ant...ab12"));
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(get("/api/settings/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anthropic.configured").value(true))
                .andExpect(jsonPath("$.anthropic.source").value("env"))
                .andExpect(jsonPath("$.anthropic.maskedKey").value("sk-ant...ab12"))
                .andExpect(jsonPath("$.openai.configured").value(false));
    }

    @Test
    void savingAValidAnthropicKeyValidatesThenPersistsIt() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-ant...wxyz"));

        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-a-real-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("saved"));

        verify(anthropicKeyValidator).validate("sk-ant-a-real-key");
        verify(secrets).setAnthropicKey("sk-ant-a-real-key");
    }

    @Test
    void savingAnInvalidAnthropicKeyReturns422AndNeverPersistsIt() throws Exception {
        doThrow(new ApiKeyValidationException("Anthropic rejected this key: invalid x-api-key"))
                .when(anthropicKeyValidator).validate("sk-ant-bad-key");

        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-bad-key\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Anthropic rejected this key: invalid x-api-key"));

        verify(secrets, org.mockito.Mockito.never()).setAnthropicKey(eq("sk-ant-bad-key"));
    }

    @Test
    void savingABlankAnthropicKeyReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingAValidOpenAiKeyValidatesThenPersistsIt() throws Exception {
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-...wxyz"));

        mockMvc.perform(put("/api/settings/keys/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-a-real-key\"}"))
                .andExpect(status().isOk());

        verify(openAiKeyValidator).validate("sk-a-real-key");
        verify(secrets).setOpenAiKey("sk-a-real-key");
    }

    @Test
    void clearingAnthropicKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(delete("/api/settings/keys/anthropic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearAnthropicKey();
    }

    @Test
    void clearingOpenAiKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(delete("/api/settings/keys/openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearOpenAiKey();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SettingsControllerTest`
Expected: FAIL — compilation error, `SettingsController`/DTOs do not exist.

- [ ] **Step 3: Write the DTOs**

`src/main/java/com/studybuddy/settings/dto/SaveKeyRequest.java`:

```java
package com.studybuddy.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveKeyRequest(

        @NotBlank(message = "apiKey must not be blank")
        String apiKey
) {
}
```

`src/main/java/com/studybuddy/settings/dto/SettingsKeysResponse.java`:

```java
package com.studybuddy.settings.dto;

import com.studybuddy.settings.RuntimeSecretsService.KeyStatus;

public record SettingsKeysResponse(KeyStatus anthropic, KeyStatus openai) {
}
```

- [ ] **Step 4: Write `SettingsController`**

```java
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=SettingsControllerTest`
Expected: PASS — 7 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/studybuddy/settings/dto/SaveKeyRequest.java \
        src/main/java/com/studybuddy/settings/dto/SettingsKeysResponse.java \
        src/main/java/com/studybuddy/settings/SettingsController.java \
        src/test/java/com/studybuddy/settings/SettingsControllerTest.java
git commit -m "feat: add SettingsController for GET/PUT/DELETE of Claude and OpenAI keys"
```

---

### Task 9: `.gitignore` + full backend verification

**Files:**
- Modify: `.gitignore` (repo root, `/Users/nareshsaharan/Desktop/genai-java-course/.gitignore`)

- [ ] **Step 1: Add the runtime secrets file to `.gitignore`**

Append this line to `/Users/nareshsaharan/Desktop/genai-java-course/.gitignore`:

```
projects/study-buddy/data/runtime-secrets.properties
```

- [ ] **Step 2: Run the full backend test suite**

Run: `cd /Users/nareshsaharan/Desktop/genai-java-course/projects/study-buddy && mvn test`
Expected: Same baseline as the start of this plan (0 failures among runnable tests; only pre-existing Testcontainers/Docker-unavailable errors if Docker isn't available).

- [ ] **Step 3: Start the app locally and smoke-test the new endpoints**

```bash
cd /Users/nareshsaharan/Desktop/genai-java-course/projects/study-buddy
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run > /tmp/study-buddy-settings-check.log 2>&1 &
sleep 10
curl -s http://localhost:8080/api/settings/keys
```

Expected: a JSON body like `{"anthropic":{"configured":true,"source":"env","maskedKey":"..."},"openai":{"configured":...}}` reflecting whatever `.env` currently has, and the app started without a validation error even if `ANTHROPIC_API_KEY` were blank (confirms Task 2's relaxation took effect). Stop the app afterward (`kill %1` or find its PID on port 8080).

- [ ] **Step 4: Commit**

```bash
cd /Users/nareshsaharan/Desktop/genai-java-course
git add .gitignore
git commit -m "chore: gitignore the runtime-secrets properties file"
```

---

## Part 2 — Frontend: tabs, two-theme redesign, Settings UI, gating

### Task 10: `styles.css` — two-theme design tokens + tab nav + settings styles

**Files:**
- Modify: `src/main/resources/static/styles.css`

- [ ] **Step 1: Replace the `:root` / dark-mode-media-query block**

Replace the file's opening block (from `:root {` through the closing `}` of the `@media (prefers-color-scheme: dark)` block — i.e. lines 1–39 of the current file) with:

```css
:root[data-theme="light"] {
  --color-bg: #F5F3FF;
  --color-surface: #FFFFFF;
  --color-border: #E0E7FF;
  --color-text: #1E1B4B;
  --color-text-muted: #6B7280;
  --color-primary: #6366F1;
  --color-primary-hover: #4F46E5;
  --color-primary-contrast: #FFFFFF;
  --color-success-bg: #ECFDF5;
  --color-success-text: #059669;
  --color-error-bg: #FEF2F2;
  --color-error-text: #DC2626;
  --color-warn-bg: #FFF7ED;
  --color-warn-text: #C2410C;
  --radius: 10px;
  --shadow: 0 1px 3px rgba(99, 102, 241, 0.08), 0 1px 2px rgba(99, 102, 241, 0.06);
  --focus-ring: 0 0 0 3px rgba(99, 102, 241, 0.35);
}

:root[data-theme="dark"] {
  --color-bg: #0F172A;
  --color-surface: #1E293B;
  --color-border: #334155;
  --color-text: #F1F5F9;
  --color-text-muted: #94A3B8;
  --color-primary: #0EA5E9;
  --color-primary-hover: #38BDF8;
  --color-primary-contrast: #0F172A;
  --color-success-bg: #0F2E23;
  --color-success-text: #34D399;
  --color-error-bg: #3A1616;
  --color-error-text: #FCA5A5;
  --color-warn-bg: #3A2A06;
  --color-warn-text: #F59E0B;
  --radius: 10px;
  --shadow: 0 1px 3px rgba(0, 0, 0, 0.5), 0 1px 2px rgba(0, 0, 0, 0.4);
  --focus-ring: 0 0 0 3px rgba(14, 165, 233, 0.4);
}
```

(Every rule elsewhere in this file already references `var(--color-*)`/`var(--radius)`/`var(--shadow)`/`var(--focus-ring)` — none of it needs to change; only these two token blocks and the new classes below are new.)

- [ ] **Step 2: Add theme-toggle, tab-nav, and settings styles**

Add this block immediately after the `.app-header` / `.app-subtitle` rules (i.e. right before the `main { ... }` rule):

```css
.header-top-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.75rem;
  position: relative;
}

.theme-toggle {
  position: absolute;
  right: 1.25rem;
  top: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  font-size: 1.1rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: auto;
}

.tab-nav {
  max-width: 760px;
  margin: 1.25rem auto 0;
  padding: 0 1.25rem;
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  justify-content: center;
}

.tab-button {
  background: transparent;
  border: 1px solid transparent;
  color: var(--color-text-muted);
  font-weight: 600;
  padding: 0.5rem 0.9rem;
  border-radius: 8px;
}

.tab-button:hover:not(:disabled) {
  background: var(--color-surface);
  color: var(--color-text);
}

.tab-button[aria-selected="true"] {
  background: var(--color-primary);
  color: var(--color-primary-contrast);
}

.settings-key-panel {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-border);
}

.settings-key-panel:first-of-type {
  margin-top: 1rem;
  padding-top: 0;
  border-top: none;
}

.settings-status {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin: 0.5rem 0 0.75rem;
  font-size: 0.9rem;
  color: var(--color-text-muted);
}

.settings-key-row {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
  align-items: center;
}

.settings-input-wrapper {
  position: relative;
  flex: 1;
  min-width: 220px;
}

.settings-input-wrapper input {
  width: 100%;
  padding-right: 2.5rem;
}

.settings-show-toggle {
  position: absolute;
  right: 0.4rem;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  padding: 0.25rem;
  font-size: 0.95rem;
  align-self: auto;
}
```

- [ ] **Step 3: Verify the file is still valid CSS**

Run: `python3 -c "import tinycss2" 2>/dev/null || pip3 install --quiet --break-system-packages tinycss2; python3 -c "
import tinycss2
with open('src/main/resources/static/styles.css') as f:
    rules = tinycss2.parse_stylesheet(f.read(), skip_whitespace=True, skip_comments=True)
errors = [r for r in rules if r.type == 'error']
print('Parse errors:', len(errors))
for e in errors: print(e)
"`
Expected: `Parse errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/styles.css
git commit -m "feat: two-theme (light Indigo Educational / dark Slate) design tokens and tab nav styles"
```

---

### Task 11: `index.html` — tabs, theme toggle, Settings tab-panel

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add the anti-flash theme script and theme toggle button to `<head>`/header**

Replace:
```html
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Study Buddy</title>
  <link rel="stylesheet" href="styles.css" />
</head>
<body>
  <a class="skip-link" href="#main-content">Skip to main content</a>

  <header class="app-header">
    <h1>Study Buddy</h1>
    <p class="app-subtitle">Upload your course notes, ask grounded questions, and generate flashcards.</p>
  </header>
```

with:
```html
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Study Buddy</title>
  <link rel="stylesheet" href="styles.css" />
  <script>
    (function () {
      var saved = localStorage.getItem('studybuddy-theme');
      var theme = saved || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      document.documentElement.setAttribute('data-theme', theme);
    })();
  </script>
</head>
<body>
  <a class="skip-link" href="#main-content">Skip to main content</a>

  <header class="app-header">
    <div class="header-top-row">
      <h1>Study Buddy</h1>
      <button type="button" id="theme-toggle-button" class="theme-toggle" aria-label="Toggle dark mode">🌙</button>
    </div>
    <p class="app-subtitle">Upload your course notes, ask grounded questions, and generate flashcards.</p>
  </header>

  <nav class="tab-nav" role="tablist" aria-label="Study Buddy sections">
    <button type="button" class="tab-button" role="tab" data-tab="settings" aria-selected="true">⚙ Settings</button>
    <button type="button" class="tab-button" role="tab" data-tab="upload" aria-selected="false">📄 Upload</button>
    <button type="button" class="tab-button" role="tab" data-tab="tutor" aria-selected="false">💬 Ask Tutor</button>
    <button type="button" class="tab-button" role="tab" data-tab="flashcard" aria-selected="false">🗂 Flashcards</button>
    <button type="button" class="tab-button" role="tab" data-tab="quiz" aria-selected="false">📝 Quiz</button>
    <button type="button" class="tab-button" role="tab" data-tab="progress" aria-selected="false">📊 Progress</button>
  </nav>
```

- [ ] **Step 2: Add `id`/`hidden` to each existing section and insert the Settings section first**

Replace the opening tag of the Upload section:
```html
    <section class="card" aria-labelledby="upload-heading">
```
with:
```html
    <!-- ============ 0. Settings ============ -->
    <section class="card" id="tab-panel-settings" aria-labelledby="settings-heading">
      <h2 id="settings-heading">API Keys</h2>
      <p class="section-hint">Keys are stored on this server only, verified against the real provider before saving, and never sent back to your browser.</p>

      <div class="settings-key-panel">
        <h3>Claude (Anthropic)</h3>
        <div class="settings-status">
          <span class="classification-badge" id="settings-anthropic-badge">Checking&hellip;</span>
          <span id="settings-anthropic-masked"></span>
        </div>
        <div class="settings-key-row">
          <div class="settings-input-wrapper">
            <input type="password" id="settings-anthropic-input" placeholder="sk-ant-..." autocomplete="off" />
            <button type="button" class="settings-show-toggle" id="settings-anthropic-show" aria-label="Show key">👁</button>
          </div>
          <button type="button" id="settings-anthropic-save">Save &amp; Verify</button>
          <button type="button" id="settings-anthropic-clear" class="secondary" hidden>Clear</button>
        </div>
        <div id="settings-anthropic-message" class="field-error" role="alert"></div>
      </div>

      <div class="settings-key-panel">
        <h3>Whisper (OpenAI) <span class="optional">(optional &mdash; for voice input)</span></h3>
        <div class="settings-status">
          <span class="classification-badge" id="settings-openai-badge">Checking&hellip;</span>
          <span id="settings-openai-masked"></span>
        </div>
        <div class="settings-key-row">
          <div class="settings-input-wrapper">
            <input type="password" id="settings-openai-input" placeholder="sk-..." autocomplete="off" />
            <button type="button" class="settings-show-toggle" id="settings-openai-show" aria-label="Show key">👁</button>
          </div>
          <button type="button" id="settings-openai-save">Save &amp; Verify</button>
          <button type="button" id="settings-openai-clear" class="secondary" hidden>Clear</button>
        </div>
        <div id="settings-openai-message" class="field-error" role="alert"></div>
      </div>
    </section>

    <!-- ============ 1. Document upload ============ -->
    <section class="card" id="tab-panel-upload" aria-labelledby="upload-heading" hidden>
```

- [ ] **Step 3: Hide the remaining sections and give them tab-panel ids**

Replace:
```html
    <section class="card" aria-labelledby="tutor-heading">
```
with:
```html
    <section class="card" id="tab-panel-tutor" aria-labelledby="tutor-heading" hidden>
```

Replace:
```html
    <section class="card" aria-labelledby="flashcard-heading">
```
with:
```html
    <section class="card" id="tab-panel-flashcard" aria-labelledby="flashcard-heading" hidden>
```

Replace:
```html
    <section class="card" aria-labelledby="quiz-heading">
```
with:
```html
    <section class="card" id="tab-panel-quiz" aria-labelledby="quiz-heading" hidden>
```

Replace:
```html
    <section class="card" aria-labelledby="progress-heading">
```
with:
```html
    <section class="card" id="tab-panel-progress" aria-labelledby="progress-heading" hidden>
```

- [ ] **Step 4: Add gating banners inside Tutor, Flashcard, Quiz sections**

Immediately after the Tutor section's opening `<h2 id="tutor-heading">Ask your tutor</h2>` line, add:
```html
      <div id="tutor-unconfigured-banner" class="status-region status-warn" role="alert" hidden>
        Add your Claude API key in the <button type="button" class="link-button" data-goto-tab="settings">Settings</button> tab to use this.
      </div>
```

Immediately after the Flashcard section's opening `<h2 id="flashcard-heading">Generate flashcards</h2>` line, add:
```html
      <div id="flashcard-unconfigured-banner" class="status-region status-warn" role="alert" hidden>
        Add your Claude API key in the <button type="button" class="link-button" data-goto-tab="settings">Settings</button> tab to use this.
      </div>
```

Immediately after the Quiz section's opening `<h2 id="quiz-heading">Take a quiz</h2>` line, add:
```html
      <div id="quiz-unconfigured-banner" class="status-region status-warn" role="alert" hidden>
        Add your Claude API key in the <button type="button" class="link-button" data-goto-tab="settings">Settings</button> tab to use this.
      </div>
```

Immediately after the voice-input section's `<div class="voice-input" aria-live="polite">` opening line (right before the `<button ... id="voice-record-button" ...>` line), add:
```html
        <div id="voice-unconfigured-banner" class="status-region status-warn" role="alert" hidden>
          Add your OpenAI API key in the <button type="button" class="link-button" data-goto-tab="settings">Settings</button> tab to use voice input.
        </div>
```

- [ ] **Step 5: Add the `.link-button` style used above**

In `src/main/resources/static/styles.css`, add this rule right after the `.tab-button` rules added in Task 10:

```css
.link-button {
  background: none;
  border: none;
  padding: 0;
  color: var(--color-warn-text);
  font-weight: 700;
  text-decoration: underline;
  align-self: auto;
  display: inline;
}
```

- [ ] **Step 6: Verify the HTML is well-formed**

Run: `python3 -c "
from html.parser import HTMLParser
class Checker(HTMLParser):
    def error(self, message):
        raise Exception(message)
with open('src/main/resources/static/index.html') as f:
    Checker().feed(f.read())
print('HTML parses without errors')
"`
Expected: `HTML parses without errors`

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/index.html src/main/resources/static/styles.css
git commit -m "feat: restructure frontend into tabs, add theme toggle and Settings tab markup"
```

---

### Task 12: `app.js` — theme toggle + tab switching

**Files:**
- Modify: `src/main/resources/static/app.js`

- [ ] **Step 1: Add the theme-toggle and tab-switching functions**

Add this block right before the `/* ---------------------------------------------------------------------\n * Bootstrap\n * ------------------------------------------------------------------- */` comment near the end of the file:

```javascript
/* ---------------------------------------------------------------------
 * Theme toggle
 * ------------------------------------------------------------------- */

function initThemeToggle() {
  const button = document.getElementById('theme-toggle-button');

  function applyIcon(theme) {
    button.textContent = theme === 'dark' ? '☀️' : '🌙';
  }

  applyIcon(document.documentElement.getAttribute('data-theme') || 'light');

  button.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('studybuddy-theme', next);
    applyIcon(next);
  });
}

/* ---------------------------------------------------------------------
 * Tab navigation
 * ------------------------------------------------------------------- */

function initTabNav() {
  const buttons = Array.from(document.querySelectorAll('.tab-button'));

  function activate(tabName) {
    for (const button of buttons) {
      const isActive = button.dataset.tab === tabName;
      button.setAttribute('aria-selected', String(isActive));
    }
    for (const panel of document.querySelectorAll('[id^="tab-panel-"]')) {
      panel.hidden = panel.id !== `tab-panel-${tabName}`;
    }
  }

  for (const button of buttons) {
    button.addEventListener('click', () => activate(button.dataset.tab));
  }

  for (const gotoButton of document.querySelectorAll('[data-goto-tab]')) {
    gotoButton.addEventListener('click', () => activate(gotoButton.dataset.gotoTab));
  }

  activate('settings');
}
```

- [ ] **Step 2: Wire both into the bootstrap block**

Replace:
```javascript
document.addEventListener('DOMContentLoaded', () => {
  initUploadSection();
  initTutorSection();
  initVoiceInputSection(document.getElementById('tutor-question'));
  initFlashcardSection();
  const progress = initProgressSection();
  initQuizSection(progress.refresh);
});
```
with:
```javascript
document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initTabNav();
  initUploadSection();
  initTutorSection();
  initVoiceInputSection(document.getElementById('tutor-question'));
  initFlashcardSection();
  const progress = initProgressSection();
  initQuizSection(progress.refresh);
});
```

- [ ] **Step 3: Verify syntax**

Run: `node --check src/main/resources/static/app.js`
Expected: no output (clean exit).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/app.js
git commit -m "feat: add theme toggle and tab-switching to the frontend"
```

---

### Task 13: `app.js` — Settings tab logic + key-configured gating

**Files:**
- Modify: `src/main/resources/static/app.js`

- [ ] **Step 1: Add the Settings API functions**

Add this block right after the existing `apiGetRecommendation` function (in the API layer section):

```javascript
async function apiGetSettingsStatus() {
  const response = await fetch('/api/settings/keys');
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiSaveKey(provider, apiKey) {
  const response = await fetch(`/api/settings/keys/${provider}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey }),
  });
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}

async function apiClearKey(provider) {
  const response = await fetch(`/api/settings/keys/${provider}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response.json();
}
```

- [ ] **Step 2: Add the Settings tab UI module and gating logic**

Add this block right before the `/* ---------------------------------------------------------------------\n * Bootstrap\n * ------------------------------------------------------------------- */` comment (after the theme/tab-nav functions added in Task 12):

```javascript
/* ---------------------------------------------------------------------
 * UI: 0. Settings (API keys) + configured-state gating
 * ------------------------------------------------------------------- */

function describeStatus(status) {
  if (!status.configured) {
    return 'Not configured';
  }
  return status.source === 'env' ? 'Using environment default' : 'Saved (custom)';
}

function initKeyPanel(provider) {
  const badge = document.getElementById(`settings-${provider}-badge`);
  const maskedLabel = document.getElementById(`settings-${provider}-masked`);
  const input = document.getElementById(`settings-${provider}-input`);
  const showToggle = document.getElementById(`settings-${provider}-show`);
  const saveButton = document.getElementById(`settings-${provider}-save`);
  const clearButton = document.getElementById(`settings-${provider}-clear`);
  const message = document.getElementById(`settings-${provider}-message`);

  function render(status) {
    badge.textContent = describeStatus(status);
    badge.className = `classification-badge classification-${status.configured ? 'not_weak' : 'weak'}`;
    maskedLabel.textContent = status.maskedKey || '';
    clearButton.hidden = status.source !== 'saved';
  }

  showToggle.addEventListener('click', () => {
    input.type = input.type === 'password' ? 'text' : 'password';
  });

  saveButton.addEventListener('click', async () => {
    message.textContent = '';
    const apiKey = input.value.trim();
    if (!apiKey) {
      message.textContent = 'Please paste a key first.';
      return;
    }

    saveButton.disabled = true;
    try {
      const status = await apiSaveKey(provider, apiKey);
      render(status);
      input.value = '';
      message.textContent = '';
      await refreshGating();
    } catch (error) {
      message.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while saving this key. Please try again.';
    } finally {
      saveButton.disabled = false;
    }
  });

  clearButton.addEventListener('click', async () => {
    message.textContent = '';
    clearButton.disabled = true;
    try {
      const status = await apiClearKey(provider);
      render(status);
      await refreshGating();
    } catch (error) {
      message.textContent = error instanceof ApiError
        ? error.message
        : 'Something went wrong while clearing this key. Please try again.';
    } finally {
      clearButton.disabled = false;
    }
  });

  return { render };
}

function applyGating(status) {
  const claudeConfigured = status.anthropic.configured;
  const openAiConfigured = status.openai.configured;

  const tutorBanner = document.getElementById('tutor-unconfigured-banner');
  const tutorButton = document.getElementById('tutor-button');
  tutorBanner.hidden = claudeConfigured;
  tutorButton.disabled = !claudeConfigured;

  const flashcardBanner = document.getElementById('flashcard-unconfigured-banner');
  const flashcardButton = document.getElementById('flashcard-button');
  flashcardBanner.hidden = claudeConfigured;
  flashcardButton.disabled = !claudeConfigured;

  const quizBanner = document.getElementById('quiz-unconfigured-banner');
  const quizGenerateButton = document.getElementById('quiz-generate-button');
  quizBanner.hidden = claudeConfigured;
  quizGenerateButton.disabled = !claudeConfigured;

  const voiceBanner = document.getElementById('voice-unconfigured-banner');
  const voiceRecordButton = document.getElementById('voice-record-button');
  voiceBanner.hidden = openAiConfigured;
  if (!openAiConfigured) {
    voiceRecordButton.disabled = true;
  } else if (typeof window.MediaRecorder !== 'undefined' && navigator.mediaDevices?.getUserMedia) {
    voiceRecordButton.disabled = false;
  }
}

let refreshGating = async () => {};

function initSettingsSection() {
  const anthropicPanel = initKeyPanel('anthropic');
  const openAiPanel = initKeyPanel('openai');

  async function loadAndApply() {
    const status = await apiGetSettingsStatus();
    anthropicPanel.render(status.anthropic);
    openAiPanel.render(status.openai);
    applyGating(status);
  }

  refreshGating = loadAndApply;
  loadAndApply();
}
```

- [ ] **Step 3: Wire `initSettingsSection` into the bootstrap block**

Replace:
```javascript
document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initTabNav();
  initUploadSection();
  initTutorSection();
  initVoiceInputSection(document.getElementById('tutor-question'));
  initFlashcardSection();
  const progress = initProgressSection();
  initQuizSection(progress.refresh);
});
```
with:
```javascript
document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initTabNav();
  initSettingsSection();
  initUploadSection();
  initTutorSection();
  initVoiceInputSection(document.getElementById('tutor-question'));
  initFlashcardSection();
  const progress = initProgressSection();
  initQuizSection(progress.refresh);
});
```

- [ ] **Step 4: Verify syntax**

Run: `node --check src/main/resources/static/app.js`
Expected: no output (clean exit).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/app.js
git commit -m "feat: add Settings tab logic and Claude/OpenAI-configured gating"
```

---

### Task 14: End-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite one more time**

Run: `cd /Users/nareshsaharan/Desktop/genai-java-course/projects/study-buddy && mvn test`
Expected: same baseline as Task 9 Step 2 — 0 failures among runnable tests.

- [ ] **Step 2: Start the app and verify in a browser**

```bash
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run > /tmp/study-buddy-final-check.log 2>&1 &
sleep 10
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080
```
Expected: `200`. Then open `http://localhost:8080` in a browser and confirm:
- The Settings tab is active by default, showing both key panels with a real status (not stuck on "Checking…").
- Clicking the sun/moon icon switches between the Indigo Educational light theme and the Slate Dark-First dark theme, and the choice survives a page reload.
- Clicking each tab button shows only that section.
- If `ANTHROPIC_API_KEY` is unset, the Tutor/Flashcards/Quiz tabs show the setup banner and their submit buttons are disabled; saving a valid key in Settings makes the banners disappear without a page reload.
- Saving an invalid key shows the provider's real rejection message inline and does not change the badge to "Saved (custom)".

- [ ] **Step 3: Stop the app**

```bash
lsof -ti:8080 | xargs -r kill
```

- [ ] **Step 4: Final commit (if any manual fixups were needed during verification)**

```bash
git status
# If clean, nothing to commit — this step only applies if Step 2 surfaced a bug that was fixed inline.
```

---

## Self-review notes

- **Spec coverage:** every row of the spec's Decisions table maps to a task above — visual style/theme switching (Task 10, 12), tabbed nav (Task 11, 12), Settings placement (Task 11), key storage/persistence (Task 1), env var precedence (Task 1), unconfigured-feature UX (Task 13), key validation (Task 5, 6, 8), `MCP_API_KEY` out of scope (untouched throughout), `ClaudeProperties.apiKey` relaxation (Task 2).
- **Type/name consistency checked:** `RuntimeSecretsService.KeyStatus(configured, source, maskedKey)` is the same shape used in `SettingsKeysResponse`, `SettingsController`, and the frontend's `describeStatus`/`render` functions throughout. `apiSaveKey`/`apiClearKey`/`apiGetSettingsStatus` names match their call sites in Task 13. `DynamicAnthropicChatModel` is referenced identically in Task 4's test and implementation.
- **No test calls a real Anthropic/OpenAI endpoint** — confirmed for every new test in Tasks 1, 4, 5, 6, 7, 8 (all use mocks or injected fake factories/probers).
