package com.studybuddy.settings;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

/**
 * Holds the currently-active Claude and OpenAI API keys for one browser
 * session. Session-scoped (not a singleton): Spring creates one instance per
 * HTTP session (identified by the standard session cookie) and transparently
 * routes calls from singleton beans (via the {@code TARGET_CLASS} scoped
 * proxy below) to whichever session's instance is active for the current
 * request.
 *
 * <p>Deliberately has <em>no</em> environment-variable fallback and no
 * cross-session persistence: this app can be a publicly hosted deployment,
 * and a stranger opening the page must never be able to use — or silently
 * overwrite — the deployer's own key. Every new session starts fully
 * unconfigured; each visitor must add their own key via the Settings UI.
 * Never logs a raw key.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RuntimeSecretsService {

    private String anthropicKey;
    private String openAiKey;

    public synchronized String getAnthropicKey() {
        return anthropicKey;
    }

    public synchronized String getOpenAiKey() {
        return openAiKey;
    }

    public synchronized KeyStatus getAnthropicStatus() {
        return status(anthropicKey);
    }

    public synchronized KeyStatus getOpenAiStatus() {
        return status(openAiKey);
    }

    public synchronized void setAnthropicKey(String newKey) {
        this.anthropicKey = newKey;
    }

    public synchronized void setOpenAiKey(String newKey) {
        this.openAiKey = newKey;
    }

    public synchronized void clearAnthropicKey() {
        this.anthropicKey = null;
    }

    public synchronized void clearOpenAiKey() {
        this.openAiKey = null;
    }

    private static KeyStatus status(String key) {
        if (!StringUtils.hasText(key)) {
            return new KeyStatus(false, "none", null);
        }
        return new KeyStatus(true, "saved", mask(key));
    }

    static String mask(String key) {
        if (key.length() <= 10) {
            return "***";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    /** API-facing view of one provider's key state — never the raw key itself. */
    public record KeyStatus(boolean configured, String source, String maskedKey) {
    }
}
