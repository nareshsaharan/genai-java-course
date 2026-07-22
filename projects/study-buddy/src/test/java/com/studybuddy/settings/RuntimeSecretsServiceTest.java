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
    void startsUnconfiguredInMockMode() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        assertThat(service.getClaudeKey()).isNull();
        RuntimeSecretsService.KeyStatus status = service.getClaudeStatus();
        assertThat(status.configured()).isFalse();
        assertThat(status.source()).isEqualTo("mock");
        assertThat(status.maskedKey()).isNull();
    }

    @Test
    void savingAKeyMakesItActiveImmediately() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        service.setClaudeKey("sk-ant-uikey6789012");

        assertThat(service.getClaudeKey()).isEqualTo("sk-ant-uikey6789012");
        assertThat(service.getClaudeStatus().source()).isEqualTo("saved");
        assertThat(service.getClaudeStatus().configured()).isTrue();
    }

    @Test
    void clearingASavedKeyRevertsToUnconfigured() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setClaudeKey("sk-ant-uikey6789012");

        service.clearClaudeKey();

        assertThat(service.getClaudeKey()).isNull();
        assertThat(service.getClaudeStatus().configured()).isFalse();
    }

    @Test
    void maskedKeyShowsOnlyPrefixAndLastFourCharacters() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setClaudeKey("sk-ant-api03-1234567890abcdef");

        String masked = service.getClaudeStatus().maskedKey();

        assertThat(masked).startsWith("sk-ant");
        assertThat(masked).endsWith("cdef");
        assertThat(masked).doesNotContain("1234567890abcdef");
    }

    @Test
    void allFiveProviderKeysAreIndependent() {
        RuntimeSecretsService service = new RuntimeSecretsService();

        service.setClaudeKey("claude-key");

        assertThat(service.getClaudeStatus().configured()).isTrue();
        assertThat(service.getGroqStatus().configured()).isFalse();
        assertThat(service.getOpenRouterStatus().configured()).isFalse();
        assertThat(service.getGeminiStatus().configured()).isFalse();
        assertThat(service.getOpenAiStatus().configured()).isFalse();
    }

    @Test
    void eachInstanceIsIndependent() {
        // Simulates two different browser sessions (two people): Spring gives
        // each one its own RuntimeSecretsService instance in production, so
        // saving a key in one must never be visible from another.
        RuntimeSecretsService sessionA = new RuntimeSecretsService();
        RuntimeSecretsService sessionB = new RuntimeSecretsService();

        sessionA.setClaudeKey("sk-ant-belongs-to-session-a");

        assertThat(sessionA.getClaudeStatus().configured()).isTrue();
        assertThat(sessionB.getClaudeStatus().configured()).isFalse();
        assertThat(sessionB.getClaudeKey()).isNull();
    }

    @Test
    void chatProviderDefaultsToClaudeAndSelectsTheMatchingKey() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setClaudeKey("claude-key");
        service.setGroqKey("groq-key");

        assertThat(service.getChatProvider()).isEqualTo(ChatProvider.CLAUDE);
        assertThat(service.getActiveChatKey()).isEqualTo("claude-key");

        service.setChatProvider(ChatProvider.GROQ);

        assertThat(service.getActiveChatKey()).isEqualTo("groq-key");
    }

    @Test
    void embeddingProviderDefaultsToOpenAiAndSelectsTheMatchingKey() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setOpenAiKey("openai-key");
        service.setGeminiKey("gemini-key");

        assertThat(service.getEmbeddingProvider()).isEqualTo(EmbeddingProvider.OPENAI);
        assertThat(service.getActiveEmbeddingKey()).isEqualTo("openai-key");

        service.setEmbeddingProvider(EmbeddingProvider.GEMINI);

        assertThat(service.getActiveEmbeddingKey()).isEqualTo("gemini-key");
    }

    @Test
    void geminiKeyIsSharedBetweenChatAndEmbeddingRoles() {
        RuntimeSecretsService service = new RuntimeSecretsService();
        service.setGeminiKey("shared-gemini-key");
        service.setChatProvider(ChatProvider.GEMINI);
        service.setEmbeddingProvider(EmbeddingProvider.GEMINI);

        assertThat(service.getActiveChatKey()).isEqualTo("shared-gemini-key");
        assertThat(service.getActiveEmbeddingKey()).isEqualTo("shared-gemini-key");
    }
}
