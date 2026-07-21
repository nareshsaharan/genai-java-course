# Study Buddy — UI Redesign + Runtime-Configurable API Keys

Date: 2026-07-21
Status: Approved by user, ready for implementation planning

## Problem

1. The current frontend (`index.html`/`styles.css`/`app.js`) is functional but visually unpolished — plain dark theme, no navigation structure, minimal visual hierarchy. The user explicitly asked for a redesign.
2. Claude (`ANTHROPIC_API_KEY`) and Whisper (`OPENAI_API_KEY`) keys are currently only configurable via environment variables, read once at Spring Boot startup and baked into immutable `@ConfigurationProperties` records (`ClaudeProperties`, `AudioProperties`) and a singleton `ChatModel` bean (`ChatModelConfig`). The user wants to enter these keys from the UI instead.

Both problems were explored together via `superpowers:brainstorming` with the visual companion (browser mockups) for style/layout questions and text Q&A for functional decisions. Design intelligence (color palettes, UI style catalog) was pulled as read-only reference data from `github.com/nextlevelbuilder/ui-ux-pro-max-skill` (its data CSVs, not its npx installer — no third-party code was executed).

## Decisions

| Question | Decision |
|---|---|
| Visual style | **Two distinct palettes, not one palette in two brightnesses**: light mode is **Indigo Educational** (primary `#6366F1`, accent/success `#059669`, background `#F5F3FF`); dark mode is **Slate Dark-First** (background `#0F172A`, primary `#0EA5E9` sky blue, accent `#F59E0B` amber) — the other visual direction from the original style comparison, reused specifically for dark mode's "modern dev-tool, easy on the eyes for long sessions" feel. Applied across every section. |
| Theme switching | **Manual toggle** (sun/moon icon in the header), not just automatic OS preference. Defaults to the system's `prefers-color-scheme` on first visit, then remembers the user's explicit choice in `localStorage` from then on. |
| Page structure | **Tabbed navigation**: Settings · Upload · Ask Tutor · Flashcards · Quiz · Progress. One tab visible at a time, client-side only (still one HTML page, no reload). |
| Settings placement | Its own tab, first in the list — always reachable, not hidden behind a modal or gear icon. |
| Key storage | **Server-side, in-memory + persisted to a local gitignored file** (`./data/runtime-secrets.properties`), so a key saved via the UI survives app restarts. |
| Env var relationship | **Both** — env vars (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) remain the seed/default. Precedence at startup: **persisted file > env var > unconfigured**. Existing `.env`/`docker-compose.yml` setups keep working unchanged. |
| Unconfigured-feature UX | Tutor/Flashcards/Quiz tabs show an inline setup banner and disable their submit controls when the relevant key isn't configured (checked via `GET /api/settings/keys`), instead of only surfacing a 503 after submission. Voice input gets the same treatment for the OpenAI key. |
| Key validation | **Validate immediately on save** — one lightweight real call per provider (Anthropic: minimal test message; OpenAI: `GET /v1/models`, cheap, doesn't consume Whisper minutes) — shown as "✓ Key verified" or the exact rejection reason before the key is persisted. |
| `MCP_API_KEY` | **Out of scope** — stays env-only. It protects a different, already-authenticated integration surface (MCP), not something meant to be edited from the browser. |

## Architecture

### Frontend

- `index.html` restructured into tab panels (each existing `<section>` becomes a tab-panel `<div>`, same inner markup/ids as today wherever possible so `app.js` DOM lookups keep working).
- New Settings tab panel: two key-entry panels (Claude, OpenAI), each with a masked input + show/hide toggle, status badge (`Not configured` / `Using environment default` / `Saved (custom)`), **Save & Verify** button, and a **Clear** button (shown only when a custom key is saved).
- `styles.css` rewritten as two token sets under `:root[data-theme="light"]` (Indigo Educational) and `:root[data-theme="dark"]` (Slate Dark-First) — deliberately different palettes per theme, not one palette's light/dark shades — applied consistently to every tab.
- A small sun/moon toggle button in the header sets `data-theme` on `<html>` and writes the choice to `localStorage`; on load, `app.js` reads a saved preference if present, else falls back to `window.matchMedia('(prefers-color-scheme: dark)')`.
- `app.js` gains: tab-switching logic (simple show/hide, one active tab class), the theme-toggle logic above, `apiGetKeySettings()`/`apiSaveKey(provider, key)`/`apiClearKey(provider)`, and a "configured" check gating the Tutor/Flashcard/Quiz/Voice submit controls, refreshed after every Settings save.

### Backend

- **`RuntimeSecretsService`** (new, `com.studybuddy.settings` package): holds current Claude/OpenAI keys in an `AtomicReference<String>` each, seeded at startup (file if present, else env var, else blank). `set(provider, key)` updates memory and rewrites the persisted properties file; `clear(provider)` removes the override and reverts to the env var (or blank). Never logs a raw key; exposes a `masked()` helper (`sk-ant-...ab12` style).
- **`DynamicAnthropicChatModel`** (new, implements `dev.langchain4j.model.chat.ChatModel`): replaces the current fixed `ChatModelConfig` bean. Reads the current key from `RuntimeSecretsService` on every call; caches a built `AnthropicChatModel` keyed by the current key value, rebuilding only when the key changes. `TutorAssistant`/`FlashcardGenerator`/`QuizGenerator` depend on this wrapper instead of a static bean — **no change to their interfaces or to any existing REST contract.**
- **`OpenAiWhisperClient`**: swap its api-key source from `AudioProperties.apiKey()` to `RuntimeSecretsService.getOpenAiKey()` — it already reads the key fresh per call rather than caching at construction, so this is a small, contained change.
- **New `SettingsController`** (`/api/settings/keys`):
  - `GET /api/settings/keys` → `{ "anthropic": { "configured": bool, "source": "env"|"saved"|"none", "maskedKey": string|null }, "openai": { ... } }`
  - `PUT /api/settings/keys/anthropic` / `PUT /api/settings/keys/openai` — body `{ "apiKey": "..." }`. Validation builds a **short-lived, throwaway** client with the *submitted* key (a one-off `AnthropicChatModel`, or a direct `GET /v1/models` call for OpenAI) — never touching `RuntimeSecretsService`'s currently-active key. Only if that validation call succeeds does the controller call `RuntimeSecretsService.set(provider, key)`, which persists it and becomes the new active key for the already-running `DynamicAnthropicChatModel`/`OpenAiWhisperClient`. Returns 200 with the masked status on success, 422 with the provider's exact rejection reason on failure (nothing is persisted on failure).
  - `DELETE /api/settings/keys/anthropic` / `DELETE /api/settings/keys/openai` — clears the override.
- **`ClaudeProperties.apiKey`**: the `@NotBlank` startup-fail-fast requirement discussed in the earlier architecture review is superseded by this feature — the app must now start even with no key configured anywhere (Settings is how you'd add one), so `apiKey` becomes nullable/optional, matching the `AudioProperties` pattern already used for Whisper.
- **`.gitignore`**: add `data/runtime-secrets.properties` (or the whole `data/` runtime directory, consistent with how `AUDIO_RECORDINGS_DIRECTORY` is already excluded).

### Testing

- `RuntimeSecretsServiceTest`: precedence (file > env > none), persistence round-trip (save → reload → same value), masking format, clear-reverts-to-env behavior.
- `DynamicAnthropicChatModelTest`: builds once per distinct key, rebuilds on key change, delegates `chat()` correctly — underlying `AnthropicChatModel` construction mocked/verified, no real network call.
- `SettingsControllerTest`: mocks `RuntimeSecretsService` and the validation call; covers save-success, save-rejected (real-looking 401 mapped to 422), clear, and the masked-status shape — **no test ever calls a real Anthropic/OpenAI endpoint**, consistent with every existing test in this codebase.
- Existing `TutorChatServiceTest`/`FlashcardServiceTest`/`QuizServiceTest`/`ChatModelConfig`-adjacent tests: unaffected, since `TutorAssistant` etc. are still mocked directly in those tests regardless of what backs `ChatModel` in production.

## Out of scope

- Multi-user/multi-tenant key storage (this app remains single-user, as established throughout the project).
- Changing `MCP_API_KEY` configuration.
- Any change to the RAG/tutor/flashcard/quiz/progress business logic — this is UI + credential-plumbing only.
