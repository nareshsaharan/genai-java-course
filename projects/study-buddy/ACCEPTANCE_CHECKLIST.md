# Study Buddy — Final Acceptance Checklist

Status as of this architecture review. ✅ = verified in this review session
(re-read the source / re-ran the command); 📝 = documented recommendation,
not applied (would change startup or API behavior — needs an explicit go-ahead).

## Build & compile
- [x] `mvn test-compile` — no compilation errors.
- [x] `mvn test` — 189 tests, **0 failures**. The 7 `ContainerFetch` errors
      are Testcontainers unable to reach Docker in this sandbox (no Docker
      daemon available), not code defects — reproduce with `docker info`
      to confirm Docker availability in your own environment.

## Dependencies
- [x] Spring Boot 3.5.16 (final 3.x GA), LangChain4j 1.17.2 BOM, Spring AI
      1.1.7 BOM (Boot-3-compatible line; 2.0 requires Boot 4 and is
      deliberately not used) — all versions pinned via BOM imports in `pom.xml`.
- [x] No unused/conflicting dependency management overrides found.

## LangChain4j / Anthropic integration
- [x] `AnthropicChatModel` built with apiKey/model/maxTokens/timeout/maxRetries
      (`ChatModelConfig`) — `temperature` only set when explicitly configured
      (some Claude models reject the parameter entirely otherwise).
- [x] `FlashcardGenerator`/`QuizGenerator` return a single wrapping record
      (`FlashcardBatch`/`GeneratedQuizBatch`), never a bare `List<T>` —
      confirmed this avoids `PojoCollectionOutputParser`'s `IllegalStateException`
      for non-native-JSON-schema providers.
- [x] `AllMiniLmL6V2EmbeddingModel` — 384-dim, in-process, no API key —
      matches `VECTOR(384)` column exactly (`EmbeddingDimensionTest`).

## pgvector / SQL
- [x] `CourseChunkSearchRepository` uses `<=>` (cosine distance) with
      `1 - distance` for similarity, parameterized, `PGvector.addVectorType`
      called on the connection before use — correct pgvector JDBC usage.
- [x] No N+1 query patterns found: all batch inserts use JDBC batching
      (`CourseChunkRepository`, `QuizRepository`), `QuizRepository.findById`
      is 2 queries total (quiz + its questions), not per-question.
- [x] Migrations V1–V11 reviewed: FKs, indexes on every foreign key and every
      commonly-filtered column, `UNIQUE` constraints where needed
      (`documents.content_hash`, `quiz_questions(quiz_id, question_index)`).

## Validation & error handling
- [x] Every request DTO has Bean Validation annotations; `GlobalExceptionHandler`
      maps every custom exception (18 handlers) plus `MethodArgumentNotValidException`,
      `HttpMessageNotReadableException`, `MaxUploadSizeExceededException`,
      `HttpMediaTypeNotSupportedException` to a consistent `ProblemDetail` schema.
- [x] Quiz submission validates the answer set exactly matches the quiz's
      question set before scoring.

## Security
- [x] Prompt injection: retrieved content explicitly labeled untrusted in
      every prompt (tutor/flashcard/quiz); dedicated test plants an
      injection attempt and asserts it's treated as inert data.
- [x] No secrets in logs (grepped every service for `apiKey`/log statements).
- [x] MCP endpoint: shared-secret header, constant-time comparison, fails
      closed (503) if unconfigured.
- [x] Frontend: no `innerHTML` with untrusted content anywhere in `app.js` —
      `textContent`/`setAttribute` only.
- [x] Filenames sanitized (basename-only) before use in any file-system path
      (audio persistence, document ingestion) — no path traversal.
- [ ] 📝 `ClaudeProperties.apiKey` is `@NotBlank` → app fails to **start**
      without `ANTHROPIC_API_KEY`, rather than starting and 503ing per-request
      like audio/MCP. Deliberate (Claude is core, not optional) but flagged —
      say the word and this can be relaxed to match the audio pattern.

## CORS
- [x] No CORS configuration exists; none is needed — frontend and API are
      same-origin (one Spring Boot app serving both). If a separate frontend
      origin is ever introduced, add an explicit `CorsConfigurationSource`
      scoped to that origin rather than a wildcard.

## Tests calling paid APIs
- [x] Grepped every test file: **zero** tests call a real Anthropic or
      OpenAI endpoint. `TutorChatIntegrationTest`/`FlashcardIntegrationTest`/
      `QuizAndProgressIntegrationTest` mock `TutorAssistant`/`FlashcardGenerator`/
      `QuizGenerator`; `AudioTranscriptionServiceTest`/`AudioControllerTest`
      mock `WhisperClient`. `application-test.yml` uses placeholder API keys
      that satisfy validation without ever being sent anywhere.

## Fixes applied in this review
- [x] `.env.example` was missing `MCP_API_KEY` (used by `McpProperties` /
      `McpApiKeyFilter`) — added with an explanatory comment.
- [x] `ChatModelConfig` now calls `.maxRetries(2)` explicitly (previously
      relied on LangChain4j's implicit default, also 2 — behavior unchanged,
      now visible in code rather than only in the library's source).
- [x] Frontend `[hidden]` CSS bug (found via live browser testing earlier
      this session, unrelated to this review pass but confirmed still fixed):
      `.voice-status`/`.loading-region`/`.flashcard-viewer` no longer override
      the native `hidden` attribute.
- [x] README/API.md corrected: `TopicClassification` values are
      `WEAK`/`NOT_WEAK`/`INSUFFICIENT_DATA` — an earlier draft of this review
      incorrectly wrote `STRONG`; caught and fixed before publishing.

## Deliverables produced in this review
- [x] [Dockerfile](Dockerfile) — multi-stage build, non-root user, healthcheck.
- [x] [docker-compose.yml](docker-compose.yml) — postgres + app, healthchecks,
      fails fast if `ANTHROPIC_API_KEY` unset.
- [x] [application-docker.yml](src/main/resources/application-docker.yml) —
      production hardening profile (stricter health exposure, connection pool sizing).
- [x] [.env.example](.env.example) — corrected/complete.
- [x] [README.md](README.md) — full rewrite: architecture, stack, quick starts, every feature, algorithm explanation, security notes.
- [x] Mermaid architecture + sequence diagrams (embedded in README).
- [x] [API.md](API.md) — every endpoint, request/response shapes, curl examples, status codes.
- [x] [DEMO_SCRIPT.md](DEMO_SCRIPT.md) — 5-minute walkthrough.
- [x] This checklist.

## Not changed (flagged only)
- `ClaudeProperties.apiKey` startup-fail-fast behavior (see Security, above).
- No code was changed beyond the two items listed under "Fixes applied" —
  every other reviewed area (LangChain4j usage, pgvector queries, SQL
  migrations, validation, exception handling, N+1 risk, frontend XSS, CORS,
  test hygiene) was found correct as-is and is documented above rather than
  modified, per "do not silently change API contracts."
