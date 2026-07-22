package com.studybuddy.settings;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

/**
 * Holds the currently-active API keys and provider selections for one
 * browser session. Session-scoped (not a singleton): Spring creates one
 * instance per HTTP session (identified by the standard session cookie) and
 * transparently routes calls from singleton beans (via the
 * {@code TARGET_CLASS} scoped proxy below) to whichever session's instance
 * is active for the current request.
 *
 * <p>Deliberately has <em>no</em> environment-variable fallback and no
 * cross-session persistence: this app can be a publicly hosted deployment,
 * and a stranger opening the page must never be able to use — or silently
 * overwrite — the deployer's own key. Every new session starts fully
 * unconfigured; each visitor must add their own key via the Settings UI —
 * or use the app as-is: while unconfigured, {@code DynamicChatModel} /
 * {@code DynamicEmbeddingModel} / {@code OpenAiWhisperClient} all serve
 * canned Mock Mode responses instead of failing, so the app stays fully
 * usable with zero API keys. Never logs a raw key.
 *
 * <p>Two independent provider choices exist per session:
 * <ul>
 *   <li>{@link ChatProvider} — powers Tutor/Flashcards/Quiz generation.
 *       Claude is the default; Groq, OpenRouter, and Gemini are free/cheap
 *       alternatives, each needing only that provider's own key.</li>
 *   <li>{@link EmbeddingProvider} — powers document embeddings for search.
 *       OpenAI is the default; Gemini is a free alternative.</li>
 * </ul>
 * Gemini's key is shared across both roles (one Google AI Studio key can
 * drive both chat and embeddings); OpenAI's key is likewise shared between
 * embeddings (when selected) and Whisper voice transcription (always, since
 * voice has no alternative provider wired up).
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RuntimeSecretsService {

    private ChatProvider chatProvider = ChatProvider.CLAUDE;
    private EmbeddingProvider embeddingProvider = EmbeddingProvider.OPENAI;

    private String claudeKey;
    private String groqKey;
    private String openRouterKey;
    private String geminiKey;
    private String openAiKey;

    public synchronized ChatProvider getChatProvider() {
        return chatProvider;
    }

    public synchronized void setChatProvider(ChatProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    public synchronized EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }

    public synchronized void setEmbeddingProvider(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    /** The key for whichever {@link ChatProvider} is currently selected — {@code null} if unconfigured. */
    public synchronized String getActiveChatKey() {
        return switch (chatProvider) {
            case CLAUDE -> claudeKey;
            case GROQ -> groqKey;
            case OPENROUTER -> openRouterKey;
            case GEMINI -> geminiKey;
        };
    }

    /** The key for whichever {@link EmbeddingProvider} is currently selected — {@code null} if unconfigured. */
    public synchronized String getActiveEmbeddingKey() {
        return switch (embeddingProvider) {
            case OPENAI -> openAiKey;
            case GEMINI -> geminiKey;
        };
    }

    public synchronized String getClaudeKey() {
        return claudeKey;
    }

    public synchronized String getGroqKey() {
        return groqKey;
    }

    public synchronized String getOpenRouterKey() {
        return openRouterKey;
    }

    public synchronized String getGeminiKey() {
        return geminiKey;
    }

    public synchronized String getOpenAiKey() {
        return openAiKey;
    }

    public synchronized KeyStatus getClaudeStatus() {
        return status(claudeKey);
    }

    public synchronized KeyStatus getGroqStatus() {
        return status(groqKey);
    }

    public synchronized KeyStatus getOpenRouterStatus() {
        return status(openRouterKey);
    }

    public synchronized KeyStatus getGeminiStatus() {
        return status(geminiKey);
    }

    public synchronized KeyStatus getOpenAiStatus() {
        return status(openAiKey);
    }

    public synchronized void setClaudeKey(String newKey) {
        this.claudeKey = newKey;
    }

    public synchronized void setGroqKey(String newKey) {
        this.groqKey = newKey;
    }

    public synchronized void setOpenRouterKey(String newKey) {
        this.openRouterKey = newKey;
    }

    public synchronized void setGeminiKey(String newKey) {
        this.geminiKey = newKey;
    }

    public synchronized void setOpenAiKey(String newKey) {
        this.openAiKey = newKey;
    }

    public synchronized void clearClaudeKey() {
        this.claudeKey = null;
    }

    public synchronized void clearGroqKey() {
        this.groqKey = null;
    }

    public synchronized void clearOpenRouterKey() {
        this.openRouterKey = null;
    }

    public synchronized void clearGeminiKey() {
        this.geminiKey = null;
    }

    public synchronized void clearOpenAiKey() {
        this.openAiKey = null;
    }

    private static KeyStatus status(String key) {
        if (!StringUtils.hasText(key)) {
            return new KeyStatus(false, "mock", null);
        }
        return new KeyStatus(true, "saved", mask(key));
    }

    static String mask(String key) {
        if (key.length() <= 10) {
            return "***";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    /**
     * API-facing view of one provider's key state — never the raw key itself.
     * {@code source} is {@code "mock"} while unconfigured (Mock Mode is
     * active for this provider) or {@code "saved"} once a real key has been
     * saved via the Settings UI for this session.
     */
    public record KeyStatus(boolean configured, String source, String maskedKey) {
    }
}
