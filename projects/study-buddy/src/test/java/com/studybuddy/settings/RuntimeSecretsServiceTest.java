package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link RuntimeSecretsService} is session-scoped in production (see the
 * class's {@code @Scope} annotation) — each browser session gets its own
 * instance, seeded unconfigured, with no environment-variable fallback and
 * no cross-session persistence. That's a deliberate choice for a publicly
 * hosted deployment: a stranger visiting the app must never be able to use
 * (or overwrite) the deployer's own key just by opening the page. These
 * tests exercise the plain-object behavior directly — Spring's session
 * scoping is orthogonal to the logic under test.
 */
class RuntimeSecretsServiceTest {

    @Test
    void startsUnconfigured() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        assertThat(service.getAnthropicKey()).isNull();
        RuntimeSecretsService.KeyStatus status = service.getAnthropicStatus();
        assertThat(status.configured()).isFalse();
        assertThat(status.source()).isEqualTo("none");
        assertThat(status.maskedKey()).isNull();
    }

    @Test
    void savingAKeyMakesItActiveImmediately() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        service.setAnthropicKey("sk-ant-uikey6789012");

        assertThat(service.getAnthropicKey()).isEqualTo("sk-ant-uikey6789012");
        assertThat(service.getAnthropicStatus().source()).isEqualTo("saved");
        assertThat(service.getAnthropicStatus().configured()).isTrue();
    }

    @Test
    void clearingASavedKeyRevertsToUnconfigured() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setAnthropicKey("sk-ant-uikey6789012");

        service.clearAnthropicKey();

        assertThat(service.getAnthropicKey()).isNull();
        assertThat(service.getAnthropicStatus().configured()).isFalse();
    }

    @Test
    void maskedKeyShowsOnlyPrefixAndLastFourCharacters() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setAnthropicKey("sk-ant-api03-1234567890abcdef");

        String masked = service.getAnthropicStatus().maskedKey();

        assertThat(masked).startsWith("sk-ant");
        assertThat(masked).endsWith("cdef");
        assertThat(masked).doesNotContain("1234567890abcdef");
    }

    @Test
    void anthropicAndOpenAiKeysAreIndependent() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        service.setAnthropicKey("sk-ant-uikey6789012");

        assertThat(service.getAnthropicStatus().configured()).isTrue();
        assertThat(service.getOpenAiStatus().configured()).isFalse();
        assertThat(service.getOpenAiKey()).isNull();
    }

    @Test
    void eachInstanceIsIndependent() {
        // Simulates two different browser sessions (two people): Spring gives
        // each one its own RuntimeSecretsService instance in production, so
        // saving a key in one must never be visible from another.
        RuntimeSecretsService sessionA = new RuntimeSecretsService();
        RuntimeSecretsService sessionB = new RuntimeSecretsService();

        sessionA.setAnthropicKey("sk-ant-belongs-to-session-a");

        assertThat(sessionA.getAnthropicStatus().configured()).isTrue();
        assertThat(sessionB.getAnthropicStatus().configured()).isFalse();
        assertThat(sessionB.getAnthropicKey()).isNull();
    }
}
