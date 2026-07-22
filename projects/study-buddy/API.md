# Study Buddy — API Reference

Base URL (local): `http://localhost:8080`. All request/response bodies are JSON
unless noted. Every error response is an [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807)
`ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "human-readable message",
  "instance": "/api/tutor/chat"
}
```

Validation failures additionally include a `fieldErrors` map (`{"topic": "must not be blank"}`).

---

## Settings (runtime-configurable API keys & provider selection)

Five independent provider keys (`claude`, `groq`, `openrouter`, `gemini`, `openai`), plus two independent selections: which provider is active for **chat** (Tutor/Flashcards/Quiz) and which is active for **embeddings** (document search). Gemini's key is shared between both roles; OpenAI's key is also always used for Whisper voice transcription regardless of the embedding-provider selection.

### `GET /api/settings/keys`

Keys are **session-scoped** (tied to the standard session cookie) — this call needs the same cookie jar as any `PUT`/`DELETE` below to see a previously-saved key; a fresh no-cookie request always comes back unconfigured.

```bash
curl -c cookie.txt -b cookie.txt http://localhost:8080/api/settings/keys
```

**200 OK**
```json
{
  "chatProvider": "claude",
  "embeddingProvider": "openai",
  "claude": { "configured": true, "source": "saved", "maskedKey": "sk-ant...ab12" },
  "groq": { "configured": false, "source": "mock", "maskedKey": null },
  "openrouter": { "configured": false, "source": "mock", "maskedKey": null },
  "gemini": { "configured": false, "source": "mock", "maskedKey": null },
  "openai": { "configured": false, "source": "mock", "maskedKey": null }
}
```
`source` ∈ `mock` (not configured for this session — Mock Mode is active for this provider, see below) / `saved` (configured via this API, for this session only — held in server memory, never on disk, never shared with any other session). `maskedKey` is never the real key. All five statuses are always present regardless of which provider is currently selected, so the UI can show every key panel at once.

### `PUT /api/settings/keys/{provider}` — `{provider}` ∈ `claude`, `groq`, `openrouter`, `gemini`, `openai`

```json
{ "apiKey": "sk-ant-..." }
```
Validates the *submitted* key against the real provider before persisting anything (one minimal chat call for `claude`/`gemini`; a cheap `GET /v1/models`-equivalent probe for `groq`/`openrouter`/`openai`) — an invalid key never overwrites a working one.

```bash
curl -c cookie.txt -b cookie.txt -X PUT http://localhost:8080/api/settings/keys/claude \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "sk-ant-your-real-key"}'
```

**200 OK** — returns the updated status for that provider (same shape as one entry from `GET`, above).

| Status | When |
|---|---|
| 400 | blank `apiKey`, or `{provider}` isn't one of the five above |
| 422 | the provider rejected the key — `detail` includes the provider's exact error message |

### `DELETE /api/settings/keys/{provider}`

Clears this session's saved key for that provider, reverting to `mock` (there is no environment-variable fallback to revert to).

```bash
curl -c cookie.txt -b cookie.txt -X DELETE http://localhost:8080/api/settings/keys/claude
```

**200 OK** — returns the reverted status for that provider.

### `PUT /api/settings/chat-provider` — `{provider}` ∈ `claude`, `groq`, `openrouter`, `gemini`

```json
{ "provider": "groq" }
```
Switches which provider powers Tutor/Flashcards/Quiz for this session — takes effect immediately, no restart. Does **not** require that provider to already have a key saved (it can be selected ahead of time, or left in Mock Mode).

```bash
curl -c cookie.txt -b cookie.txt -X PUT http://localhost:8080/api/settings/chat-provider \
  -H "Content-Type: application/json" \
  -d '{"provider": "groq"}'
```

**200 OK** — returns the full status shape (same as `GET /api/settings/keys`).

| Status | When |
|---|---|
| 400 | blank `provider`, or not one of `claude`/`groq`/`openrouter`/`gemini` |

### `PUT /api/settings/embedding-provider` — `{provider}` ∈ `openai`, `gemini`

```json
{ "provider": "gemini" }
```
Switches which provider powers document embeddings for this session.

```bash
curl -c cookie.txt -b cookie.txt -X PUT http://localhost:8080/api/settings/embedding-provider \
  -H "Content-Type: application/json" \
  -d '{"provider": "gemini"}'
```

**200 OK** — returns the full status shape (same as `GET /api/settings/keys`).

| Status | When |
|---|---|
| 400 | blank `provider`, or not one of `openai`/`gemini` |

---

## Documents

### `POST /api/documents/upload`

Multipart form upload. Ingests a course-notes file: extracts text, dedupes by
content hash, chunks (~400 tokens / 40 overlap by default), embeds via the
session's selected [embedding provider](README.md#multi-provider-chat--embeddings-free-alternatives-to-claudeopenai)
(OpenAI `text-embedding-3-small` or Gemini `gemini-embedding-001`, both
truncated/configured to 384 dims), stores in `course_chunks`. No API key
required: while unconfigured, Mock Mode embeds with a deterministic
pseudo-vector instead (see [README.md](README.md#mock-mode--using-the-app-with-zero-api-keys)),
so upload keeps working end to end.

| Part | Type | Required |
|---|---|---|
| `file` | PDF / TXT / Markdown | yes |
| `topic` | text, ≤200 chars | no |

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@spring-notes.pdf" \
  -F "topic=Spring Boot"
```

**201 Created**
```json
{
  "documentId": "b3f1...",
  "sourceFilename": "spring-notes.pdf",
  "chunkCount": 12,
  "status": "INGESTED"
}
```
`status` is `INGESTED` or `DUPLICATE` (re-uploading identical content returns
the original document's id/chunkCount, HTTP 201, no new rows written).

| Status | When |
|---|---|
| 415 | Unsupported file type, or missing/wrong multipart Content-Type |
| 422 | Empty file / no extractable text |
| 413 | File exceeds `spring.servlet.multipart.max-file-size` (25MB) |

---

## Tutor chat

### `POST /api/tutor/chat`

Retrieval-augmented Q&A grounded in ingested course notes when relevant
content is found; otherwise falls back to the chat provider's own general
knowledge, clearly flagged as such (see `confidence` below).

```json
{
  "question": "Explain dependency injection",
  "topic": "Spring Boot"
}
```
`topic` is optional (omit or `null` to search across all ingested content).

```bash
curl -X POST http://localhost:8080/api/tutor/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Explain dependency injection", "topic": "Spring Boot"}'
```

**200 OK**
```json
{
  "answer": "Dependency injection is a design pattern where...",
  "confidence": "HIGH",
  "sources": [
    {
      "chunkId": "a1b2...",
      "sourceFile": "spring-notes.pdf",
      "chunkIndex": 8,
      "snippet": "Dependency injection is a design pattern...",
      "similarityScore": 0.87
    }
  ]
}
```
`confidence` is `HIGH` / `MEDIUM` / `LOW` (based on top retrieved similarity
score, answer grounded strictly in the retrieved chunks) or
`NO_RELEVANT_CONTEXT` (no chunk cleared `RAG_MIN_SCORE` — `sources` is empty,
and the chat provider is still called, but answers from its own general
knowledge instead, with the answer text itself stating up front that it
isn't grounded in the uploaded notes). No API keys required: while
unconfigured, both retrieval and the answer itself run in Mock Mode (see
[README.md](README.md#mock-mode--using-the-app-with-zero-api-keys)) instead of
returning a 503.

| Status | When |
|---|---|
| 400 | blank `question` |
| 502 | chat provider call failed for a reason other than timeout |
| 504 | chat provider call timed out |

---

## Flashcards

### `POST /api/flashcards`

```json
{
  "topic": "RAG",
  "count": 5,
  "difficulty": "MEDIUM"
}
```
`difficulty` ∈ `EASY` / `MEDIUM` / `HARD`. `count` ∈ [1, 20].

```bash
curl -X POST http://localhost:8080/api/flashcards \
  -H "Content-Type: application/json" \
  -d '{"topic": "RAG", "count": 5, "difficulty": "MEDIUM"}'
```

**200 OK**
```json
{
  "cards": [
    {
      "id": "c4d5...",
      "question": "What does RAG stand for?",
      "answer": "Retrieval-Augmented Generation.",
      "topic": "RAG",
      "difficulty": "MEDIUM",
      "sourceChunkIds": ["a1b2...", "e6f7..."]
    }
  ]
}
```
Cards are generated only from retrieved course-note context (never from the
chat model's general knowledge), validated, de-duplicated (Levenshtein similarity,
no regex), and capped at `count` — fewer cards than requested is possible and
not an error. No API keys required: while unconfigured, this returns example
cards from Mock Mode instead of a 503.

| Status | When |
|---|---|
| 404 | No course content found for `topic` (nothing relevant ingested yet) |
| 502 / 504 | chat provider call failed / timed out |

---

## Quizzes

### `POST /api/quizzes/generate`

Same request shape as flashcards (`topic`, `count`, `difficulty`). No API keys
required: while unconfigured, this returns an example quiz from Mock Mode
instead of a 503.

```bash
curl -X POST http://localhost:8080/api/quizzes/generate \
  -H "Content-Type: application/json" \
  -d '{"topic": "RAG", "count": 3, "difficulty": "MEDIUM"}'
```

**200 OK** — **never reveals the correct answer** at this stage:
```json
{
  "quizId": "f0a1...",
  "topic": "RAG",
  "questions": [
    {
      "questionId": "1a2b...",
      "questionIndex": 0,
      "questionText": "What does RAG stand for?",
      "options": ["Retrieval-Augmented Generation", "Random Access Generator", "Rapid Answer Grid", "Recursive Agent Graph"]
    }
  ]
}
```

| Status | When |
|---|---|
| 404 | No course content found for `topic` (nothing relevant ingested yet) |
| 502 / 504 | chat provider call failed / timed out |

### `POST /api/quizzes/{quizId}/submit`

```json
{
  "answers": [
    { "questionId": "1a2b...", "selectedOptionIndex": 0 }
  ]
}
```
Submission must include exactly the quiz's question set (no more, no fewer;
every `questionId` must belong to this quiz) or `400 QuizSubmissionException`.

```bash
curl -X POST http://localhost:8080/api/quizzes/f0a1.../submit \
  -H "Content-Type: application/json" \
  -d '{"answers": [{"questionId": "1a2b...", "selectedOptionIndex": 0}]}'
```

**200 OK** — correct answers are revealed only now, and this call also
updates recency-weighted topic progress:
```json
{
  "attemptId": "9c8d...",
  "topic": "RAG",
  "correctCount": 1,
  "totalCount": 1,
  "accuracy": 1.0,
  "results": [
    { "questionId": "1a2b...", "selectedOptionIndex": 0, "correctOptionIndex": 0, "correct": true }
  ]
}
```

| Status | When |
|---|---|
| 404 | `quizId` doesn't exist |
| 400 | Submission doesn't match the quiz's question set |

---

## Progress

### `GET /api/progress/topics`
All topics with any quiz history, newest-updated or not — full list, no filtering.

### `GET /api/progress/weak-topics`
Subset of the above currently classified `WEAK` (enough attempts, accuracy
below threshold). See [README § weak-topic scoring](README.md#weak-topic-scoring-algorithm).

### `GET /api/progress/recommendation`
Single next-topic suggestion with a human-readable reason.

```bash
curl http://localhost:8080/api/progress/topics
curl http://localhost:8080/api/progress/weak-topics
curl http://localhost:8080/api/progress/recommendation
```

**200 OK** (`/topics`, `/weak-topics` — array of):
```json
{
  "topic": "RAG",
  "correctCount": 3,
  "totalCount": 5,
  "accuracy": 0.58,
  "lastAttemptAt": "2026-07-20T10:15:30Z",
  "classification": "WEAK"
}
```
`classification` ∈ `WEAK` / `NOT_WEAK` / `INSUFFICIENT_DATA`.

**200 OK** (`/recommendation`):
```json
{ "topic": "RAG", "reason": "Lowest accuracy among weak topics (58% over 5 questions).", "accuracy": 0.58, "totalCount": 5 }
```

| Status | When |
|---|---|
| 404 (`/recommendation` only) | No quiz history yet, or nothing currently weak |

---

## Voice input (optional)

### `POST /api/audio/transcribe`

No OpenAI key required: while unconfigured for this session, Mock Mode
returns a canned transcript instead of a 503 (see
[README.md](README.md#mock-mode--using-the-app-with-zero-api-keys)).
Recordings are never written to disk unless `AUDIO_PERSIST_RECORDINGS=true`;
the staged temp file is always deleted after the call regardless of
success/failure.

| Part | Type | Required |
|---|---|---|
| `file` | flac/mp3/mp4/mpeg/mpga/m4a/ogg/wav/webm | yes |

```bash
curl -X POST http://localhost:8080/api/audio/transcribe \
  -F "file=@question.wav"
```

**200 OK**
```json
{ "transcript": "Explain dependency injection", "language": "english", "durationSeconds": 4.2 }
```

| Status | When |
|---|---|
| 415 | Unsupported audio format |
| 413 | File exceeds `AUDIO_MAX_FILE_SIZE_BYTES`, or estimated duration exceeds `AUDIO_MAX_DURATION_SECONDS` |
| 422 | Empty/unreadable file |
| 502 / 504 | Whisper call failed / timed out |

---

## MCP server (separate profile)

Not a REST endpoint — a single `POST /mcp` JSON-RPC (Streamable HTTP)
endpoint, only active with `--spring.profiles.active=mcp`, requiring header
`X-MCP-Api-Key: <MCP_API_KEY>`. See [README § MCP server](README.md#mcp-server)
for the 5 tools, example tool calls, and Claude Desktop/Code connection config.

---

## Operational

### `GET /actuator/health`
```bash
curl http://localhost:8080/actuator/health
```
```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"PostgreSQL","validationQuery":"isValid()"}},"diskSpace":{"status":"UP"},"ping":{"status":"UP"}}}
```

### `GET /actuator/metrics/{name}`
Notable custom metrics (all tagged `feature=tutor|flashcard|quiz|audio`):
`studybuddy.ingestion.duration`, `studybuddy.retrieval.latency`,
`studybuddy.claude.latency`, `studybuddy.no_context.count`,
`studybuddy.model.failure.count`.
```bash
curl http://localhost:8080/actuator/metrics/studybuddy.claude.latency
```
