# Study Buddy — 5-Minute Demonstration Script

Prerequisites: `docker compose up --build` running (or local `mvn spring-boot:run`
with Postgres up), `ANTHROPIC_API_KEY` set to a real key, browser open to
`http://localhost:8080`. A short sample notes file is enough — 1-2 paragraphs
on any topic works (e.g. "Spring Boot", "RAG", "Dependency Injection").

Total: ~5 minutes. Each step includes the UI action and the equivalent curl.

---

### 1. Upload course notes (30s)

**UI**: "Upload course notes" card → choose a `.txt`/`.md`/`.pdf` file → type
topic `Spring Boot` → **Upload**. Confirm the success message shows a
`chunkCount > 0`.

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@spring-notes.txt" -F "topic=Spring Boot"
```

**Say**: "The file is chunked, embedded locally with all-MiniLM-L6-v2 — no
API call for this step — and stored in Postgres with pgvector."

---

### 2. Ask the tutor a grounded question (60s)

**UI**: "Ask your tutor" card → type a question the notes actually answer →
**Ask**. Point out the **confidence badge** and the **Sources** expander
(source file + chunk index + similarity score).

```bash
curl -X POST http://localhost:8080/api/tutor/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Explain dependency injection", "topic": "Spring Boot"}'
```

**Then ask something *not* in the notes** (e.g. "What's the capital of
France?") and show the response is `NO_RELEVANT_CONTEXT` with the fixed
"not enough information" answer — **not** a guess from Claude's general
knowledge, and Claude was never even called for it (point at the log line or
`studybuddy.no_context.count` metric).

**Say**: "This is the core guardrail: the model only ever sees retrieved
course-note text, labeled explicitly as untrusted data it must not follow as
instructions — even if the notes themselves contained something that looked
like a command."

---

### 3. Generate flashcards (45s)

**UI**: "Generate flashcards" card → topic `Spring Boot`, count `5`,
difficulty `Medium` → **Generate**. Flip through 2-3 cards.

```bash
curl -X POST http://localhost:8080/api/flashcards \
  -H "Content-Type: application/json" \
  -d '{"topic": "Spring Boot", "count": 5, "difficulty": "MEDIUM"}'
```

**Say**: "Same retrieval-then-ground pipeline as tutor chat. The model
returns typed structured output — a real Java object, not text we regex-parse
— and near-duplicate cards are removed automatically."

---

### 4. Take a quiz and see progress update (90s)

Generate + submit a quiz (UI not built for this in the demo frontend — use
curl, or note it's the same "Ask your tutor"-style flow conceptually):

```bash
QUIZ=$(curl -s -X POST http://localhost:8080/api/quizzes/generate \
  -H "Content-Type: application/json" \
  -d '{"topic": "Spring Boot", "count": 3, "difficulty": "MEDIUM"}')
echo "$QUIZ" | python3 -m json.tool   # note: no correct answers shown yet
QUIZ_ID=$(echo "$QUIZ" | python3 -c "import json,sys; print(json.load(sys.stdin)['quizId'])")
Q1=$(echo "$QUIZ" | python3 -c "import json,sys; print(json.load(sys.stdin)['questions'][0]['questionId'])")

curl -X POST http://localhost:8080/api/quizzes/$QUIZ_ID/submit \
  -H "Content-Type: application/json" \
  -d "{\"answers\": [{\"questionId\": \"$Q1\", \"selectedOptionIndex\": 0}]}"
```

Then:
```bash
curl http://localhost:8080/api/progress/topics
curl http://localhost:8080/api/progress/recommendation
```

**Say**: "Submitting updates a recency-weighted accuracy score per topic —
not a flat lifetime average, so a bad recent quiz matters more than an old
good one. Once a topic has enough attempts, it's classified weak or not, and
the recommendation engine rotates between near-tied weak topics rather than
fixating on one."

---

### 5. Voice input, optional (30s, only if `OPENAI_API_KEY` is set)

**UI**: "Ask your tutor" → click **🎤 Record voice question** → allow mic →
speak a question → **Stop** → review the playback → **Use this recording**.
Point out the transcript appears in the question box **without auto-submitting**
— the student still clicks **Ask** themselves.

If `OPENAI_API_KEY` isn't set, show the graceful failure instead:
```bash
curl -X POST http://localhost:8080/api/audio/transcribe -F "file=@sample.wav"
# 503 — "Voice input is not configured on this server"
```

---

### 6. Wrap-up: health & metrics (15s)

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/studybuddy.claude.latency
```

**Say**: "Every feature is instrumented — retrieval latency, Claude latency,
no-context rate, failure rate — tagged by feature, with structured JSON logs
correlated by request id. Nothing here ever logs the actual question, answer,
or document content."

---

**Optional bonus** (if time remains): MCP tools via `curl -X POST /mcp` with
`X-MCP-Api-Key`, or connect Claude Desktop/Code per [README § MCP server](README.md#mcp-server)
and ask it "what am I weak at?" conversationally.
