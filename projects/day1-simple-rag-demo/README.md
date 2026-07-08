# Day 1: Simple RAG Demo

A beginner-friendly Spring Boot project that will grow, lesson by lesson,
into a **manual RAG (Retrieval-Augmented Generation) pipeline** built from
scratch — no agents, no ReAct, no memory, no local model routing. Just the
core ideas behind RAG, kept as simple and readable as possible.

## What is RAG, in plain words?

RAG lets an AI answer questions using *your* documents instead of only what
it memorized during training. It works in 6 steps:

1. **Document ingestion** — bring raw text into the system.
2. **Chunking** — split big text into small, digestible pieces.
3. **Embeddings** — turn each chunk into a list of numbers representing its meaning.
4. **Vector store** — save chunks + their number-vectors somewhere searchable (in-memory for this demo).
5. **Top-k search** — given a question, find the most relevant chunks by comparing vectors.
6. **RAG answer generation** — feed the relevant chunks + the question to an LLM to produce the final answer.

## What's in Day 1?

We now have a working Spring Boot app covering **all 6 steps of RAG**:
document ingestion, chunking, embeddings, vector storage, top-k search,
and (mock) answer generation. Ingesting a document stores it, splits it
into overlapping chunks, and embeds every chunk into the vector store.
`/api/search` retrieves relevant chunks for a query. `/api/rag/ask`
goes one step further: it retrieves chunks AND uses a mock LLM to turn
them into an answer.

```
com.example.day1rag
├── Day1SimpleRagDemoApplication.java   # Main class — starts the app
├── controller/
│   ├── HealthController.java           # GET /api/health
│   ├── DocumentController.java         # POST /api/documents/ingest
│   ├── SearchController.java           # POST /api/search
│   └── RagController.java              # POST /api/rag/ask
├── service/
│   ├── DocumentService.java            # ingestion orchestration
│   ├── ChunkingService.java            # splits text into overlapping chunks
│   ├── EmbeddingService.java           # interface: text -> vector
│   ├── FakeEmbeddingService.java       # word-hashing "fake" embedding implementation
│   ├── RetrievalService.java           # step 5: retrieval / search orchestration
│   ├── RagService.java                 # step 6: builds the prompt, calls the LLM, shapes the response
│   ├── LlmService.java                 # interface: prompt -> answer
│   └── MockLlmService.java             # fake "LLM" implementation, no external calls
├── model/
│   ├── DocumentIngestRequest.java      # incoming JSON body for ingestion
│   ├── DocumentRecord.java             # stored form of a document
│   ├── DocumentChunk.java              # a single chunk + its metadata
│   ├── VectorRecord.java               # a chunk paired with its embedding
│   ├── SearchRequest.java              # incoming JSON body for search
│   ├── SearchResponse.java             # outgoing JSON body for search
│   ├── SearchResult.java               # one retrieved chunk + score + metadata
│   ├── RagRequest.java                 # incoming JSON body for /api/rag/ask
│   └── RagResponse.java                # outgoing JSON body for /api/rag/ask
├── store/
│   ├── InMemoryDocumentStore.java      # simple Map-based document storage
│   └── InMemoryChunkStore.java         # simple Map-based chunk storage
├── vector/
│   ├── InMemoryVectorStore.java        # stores chunk+vector pairs and runs top-k search
│   ├── CosineSimilarity.java           # measures how close two vectors are
│   └── ScoredVectorRecord.java         # a VectorRecord + its similarity score (internal search result)
└── config/                             # (empty today) — nothing needed yet
```

## What is chunking?

Chunking means cutting a long document into smaller pieces of text so
each piece is small enough and focused enough to be embedded and
searched effectively. Think of it like cutting a long article into
paragraphs before highlighting the parts that matter — it's easier to
find (and hand to an LLM) three relevant paragraphs than to hand over
the whole article.

This demo uses the simplest possible strategy: **character-based
chunking**. It cuts the text every `chunkSize` characters, without any
awareness of words or sentences. It's not the smartest approach, but
it's the easiest to understand as a first step.

Both settings live in `application.properties` so you can experiment
without touching code:

```properties
rag.chunk.size=500
rag.chunk.overlap=100
```

### Why overlap?

Without overlap, a sentence that happens to fall right on a chunk
boundary gets cut in half — one chunk ends mid-thought and the next
chunk starts mid-thought, so neither chunk fully captures that idea.

Overlap fixes this by repeating the last `overlap` characters of one
chunk at the start of the next chunk. That way, any idea near a
boundary is fully readable in at least one chunk.

Example with `chunkSize=500` and `overlap=100`: chunk 1 covers
characters 0–500, chunk 2 covers 400–900 (it "rewinds" 100 characters),
chunk 3 covers 800–1300, and so on.

### What happens if a chunk is too small or too large?

- **Too small** (e.g. 50 characters): you get a lot of chunks, many of
  which are cut off mid-sentence and don't contain a complete thought.
  Search results become noisy because tiny, fragmented chunks rarely
  contain a full, meaningful answer.
- **Too large** (e.g. 5000 characters): each chunk mixes multiple,
  unrelated ideas together. This makes it harder to find a chunk that's
  *specifically* about the question being asked, and later on it wastes
  space in the LLM's prompt when we hand over "relevant" chunks that are
  actually mostly irrelevant.

A good chunk size is usually a few sentences to a short paragraph —
big enough to hold a complete idea, small enough to stay focused.

## What are embeddings?

An embedding is a fixed-size list of numbers (a "vector") that's meant
to represent the *meaning* of a piece of text. Computers can't compare
"meaning" directly, but they can compare numbers — so once text becomes
a vector, we can measure how "close" two pieces of text are just by
doing math on their vectors. That's what will power search in a later
lesson: turn the user's question into a vector too, then find the
stored chunks whose vectors are closest to it.

**In production**, embeddings come from a real, trained AI model (for
example, OpenAI's or Anthropic-compatible embedding models). These
models are trained on huge amounts of text so that sentences with
similar meaning end up with similar vectors — even when they don't
share any of the same words (e.g. "car" and "automobile" would land
close together).

**In this demo**, we use a `FakeEmbeddingService` instead — no API key,
no external calls, just plain Java, so students can see the *shape* of
an embedding pipeline before using a real one. It works like this:

1. Split the chunk's text into lowercase words.
2. Hash each word and use the hash to pick one of 64 "slots" (our
   vector has 64 dimensions).
3. Add `1.0` into that slot every time a word lands there.
4. Normalize the vector (scale it to length 1.0), so comparisons are
   fair regardless of how long the original text was.

This fake embedding only understands "which words appear," not real
meaning — two chunks that share a lot of the same words will look
similar to it, but it wouldn't know that "car" and "automobile" are
related. That's exactly the limitation a real embedding model solves,
and it's why production RAG systems use one.

## What is cosine similarity?

Once text has become a vector, we need a way to measure how "close"
two vectors are — that's `CosineSimilarity`. It compares the *angle*
between two vectors, ignoring their length:

- **Higher score = more similar.**
- A score **near 1** means the two vectors point in almost the same
  direction — highly similar.
- A score **near 0** means the two vectors are unrelated to each other.
- (A score near -1 means opposite directions, though that's rare with
  our word-count-style fake embeddings.)

The formula is just: `(A · B) / (|A| × |B|)` — the dot product of the
two vectors, divided by the product of their lengths. No external math
library needed; it's about a dozen lines of plain Java (see
[`CosineSimilarity.java`](src/main/java/com/example/day1rag/vector/CosineSimilarity.java)).

### Example

[`CosineSimilarityTest.java`](src/test/java/com/example/day1rag/vector/CosineSimilarityTest.java)
embeds two short phrases with `FakeEmbeddingService` and compares them:

```java
similarity("the car is fast", "the automobile is fast")  // = 0.75
similarity("the car is fast", "i ate a banana")           // = 0.0
```

The first pair scores higher because it shares three words ("the",
"is", "fast") — **not** because the fake embedding understands that a
car and an automobile are the same kind of thing. That's the exact
limitation described above: a real embedding model would score "car"
vs "automobile" highly even without any shared words, because it
understands meaning, not just word overlap.

Run it yourself:
```bash
mvn test -Dtest=CosineSimilarityTest
```

## What is vector search (top-k search)?

Now that every chunk has a vector, and we have a way to compare
vectors (cosine similarity), we can finally answer: *"which stored
chunks are most relevant to this question?"* That's what
`InMemoryVectorStore.search(query, topK)` does:

1. Convert the incoming query text into an embedding — the exact same
   way every chunk was embedded during ingestion.
2. Compare the query's vector against **every** stored chunk vector
   using `CosineSimilarity`.
3. Sort all the results by score, highest (most similar) first.
4. Return only the top `topK` results.

This is a brute-force scan — simple and easy to follow, but it checks
every single stored vector one by one. That's perfectly fine for a
classroom demo with a handful of documents; real vector databases use
smarter indexing to stay fast with millions of vectors.

### Classroom note: search ≠ answering

**`/api/search` only retrieves chunks — it does not generate a final
answer.** It hands back the raw text of the most relevant chunks plus
their similarity scores, so you (or an LLM) can decide what to do with
them. Turning these chunks into a natural language answer is "RAG
answer generation" (step 6), covered next.

## What is RAG answer generation?

This is the final step: take the retrieved chunks, combine them with
the original question into one prompt, and ask an LLM to answer —
**using only that context**. `RagService` builds this exact prompt:

```
Use only the context below to answer the question.

Context:
{{retrieved_chunks}}

Question:
{{question}}

Rules:
- Answer only from the context.
- If answer is not present in the context, say:
  "I don't know from the provided documents."
```

`{{retrieved_chunks}}` is filled in with the text of every chunk
`RetrievalService` found, and `{{question}}` with the user's question.
This "rules" section is what keeps the model grounded — instead of
answering from whatever it might already know, it's told to only use
what's in the context, and to admit when the answer isn't there.

**`MockLlmService`** stands in for a real model on Day 1: it does not
call any external API. It simply reads the context back out of the
prompt and returns it as the answer, phrased as
`"Based on the provided course notes, ..."` (or the "I don't know..."
message if no context was retrieved) — enough to prove the whole
pipeline works end-to-end. A later lesson swaps this for a real LLM
call, and nothing else in the pipeline needs to change.

**Classroom note:** every final prompt is printed to the application
logs before it's sent to the LLM, so you can see exactly what context
and instructions the model receives.

### Why citations (sources) matter

Every response from `/api/rag/ask` includes a `sources` list — one
entry per retrieved chunk, with exactly where it came from:

```json
{
  "documentId": "course-notes",
  "title": "GenAI Course Notes",
  "chunkIndex": 2,
  "score": 0.91
}
```

Without this, an "answer" is just text — there's no way to check
whether it's actually grounded in the documents, or which specific
part of which document it came from. Citations turn a black-box answer
into something a reader can *verify*: open `course-notes`, look at
chunk 2, and confirm the answer really does say what the response
claims.

**In production, this "source traceability" isn't optional.** A few
reasons it's required, not just nice-to-have:

- **Trust** — users (and regulators, in some industries) need to know
  an AI answer is grounded in a real document, not invented ("hallucinated").
- **Auditability** — if an answer turns out to be wrong or outdated,
  you need to trace it back to the exact document and chunk that
  produced it, so you can fix the source data.
- **Debugging retrieval quality** — the `score` on each source tells
  you *how* confident the match was. Consistently low scores across
  many queries is a signal that chunking, embeddings, or `topK` need
  tuning — the same signal that powers the safety net above.
- **Compliance** — some domains (legal, medical, financial) require
  showing exactly which source material backs a generated answer.

This is exactly why `RagResponse` returns both `sources` (a compact
citation list) and `retrievedChunks` (the full chunk text) — together
they let anyone reading the response verify the answer themselves,
instead of just trusting it.

### Safety net: what if nothing relevant was found?

Telling the LLM to "only answer from the context" isn't enough on its
own — if the retrieved chunks are actually irrelevant (just the
*least* bad matches out of what's stored), a real LLM might still try
to force an answer out of them. So `RagService` adds a second,
code-level safety check, before the LLM is even involved:

1. Retrieve the `topK` chunks, as always.
2. Look at the **highest** similarity score among them.
3. If that highest score is below `rag.min-score` (configurable in
   `application.properties`, default `0.25`), the retrieved chunks
   aren't trustworthy enough — **skip the LLM call entirely** and
   return the safe answer directly.

```properties
rag.min-score=0.25
```

When this happens, the response includes an extra `"reason"` field
explaining why (this field is only present when the LLM was skipped —
a normal, successful answer won't have it):

```json
{
  "question": "What is the best pizza topping in Italy?",
  "answer": "I don't know from the provided documents.",
  "reason": "No retrieved chunk passed minimum similarity threshold",
  "sources": [],
  "retrievedChunks": [
    {
      "chunkText": "RAG is Retrieval Augmented Generation.",
      "score": 0.13363062095621223,
      "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 0 }
    }
  ]
}
```

Note that `retrievedChunks` still shows what *was* found — the low
score is exactly why they were rejected, and seeing them helps explain
the decision rather than hiding it.

## Requirements

- Java 21
- Maven

## Running the app

```bash
cd projects/day1-simple-rag-demo
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

## API

### `GET /api/health`

Checks that the app is running.

**Response**
```json
{
  "status": "UP",
  "message": "Day 1 RAG demo is running"
}
```

**Try it**
```bash
curl http://localhost:8080/api/health
```

### `POST /api/documents/ingest`

Stores a document in memory, splits it into overlapping chunks, and
creates a (fake) embedding vector for every chunk — chunk + vector
pairs are added to the in-memory vector store, ready to be searched.

**Request**
```json
{
  "documentId": "course-notes",
  "title": "GenAI Course Notes",
  "content": "RAG means Retrieval-Augmented Generation..."
}
```

**Response**
```json
{
  "documentId": "course-notes",
  "status": "INGESTED",
  "chunksCreated": 4
}
```

**Try it**
```bash
curl -X POST http://localhost:8080/api/documents/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "course-notes",
    "title": "GenAI Course Notes",
    "content": "RAG means Retrieval-Augmented Generation..."
  }'
```

While this runs, check the application logs — each chunk's index and a
100-character preview are printed so you can see exactly how the text
was split.

### `POST /api/search`

Retrieves the `topK` chunks most relevant to a query, using the
vector search flow described above. **This endpoint only retrieves —
it does not generate a final answer.**

**Request**
```json
{
  "query": "What is RAG?",
  "topK": 3
}
```

**Response**
```json
{
  "query": "What is RAG?",
  "results": [
    {
      "chunkText": "...",
      "score": 0.89,
      "metadata": {
        "documentId": "course-notes",
        "title": "GenAI Course Notes",
        "chunkIndex": 1
      }
    }
  ]
}
```

**Try it** (ingest a document first, then search it):
```bash
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is RAG?",
    "topK": 3
  }'
```

### `POST /api/rag/ask`

Runs the full RAG pipeline: retrieve the `topK` most relevant chunks,
check that at least one of them is similar enough to trust (see the
safety net above), then build a prompt and ask the (mock) LLM to
answer using only that context. **This is the only endpoint that
generates an answer — `/api/search` just retrieves.**

**Request**
```json
{
  "question": "What is RAG?",
  "topK": 3
}
```

**Response (relevant question — a real answer)**
```json
{
  "question": "What is RAG?",
  "answer": "Based on the provided course notes, ...",
  "sources": [
    {
      "documentId": "course-notes",
      "title": "GenAI Course Notes",
      "chunkIndex": 2,
      "score": 0.91
    }
  ],
  "retrievedChunks": [
    {
      "chunkText": "...",
      "score": 0.91,
      "metadata": {
        "documentId": "course-notes",
        "title": "GenAI Course Notes",
        "chunkIndex": 2
      }
    }
  ]
}
```

**Response (question not covered by the documents — safe fallback)**
```json
{
  "question": "What is the best pizza topping in Italy?",
  "answer": "I don't know from the provided documents.",
  "reason": "No retrieved chunk passed minimum similarity threshold",
  "sources": [],
  "retrievedChunks": [
    { "chunkText": "...", "score": 0.13, "metadata": { "...": "..." } }
  ]
}
```

`answer` is always exactly `"I don't know from the provided documents."`
whenever no retrieved chunk's score meets `rag.min-score` — including
when nothing has been ingested yet, or `topK: 0` is requested.

**Try it** (ingest a document first, then ask a question about it):
```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is RAG?",
    "topK": 3
  }'
```

**Try asking something the document doesn't cover** (to see the safety
fallback in action):
```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the best pizza topping in Italy?",
    "topK": 3
  }'
```

While this runs, check the application logs — the exact final prompt
sent to the LLM is printed there (or, for the fallback case, a log
line explaining the LLM call was skipped).

## Full end-to-end demo

This walks through the entire pipeline using the bundled sample
document, [`sample-course-notes.txt`](src/main/resources/sample-course-notes.txt),
which covers prompt engineering, embeddings, vector databases,
semantic search, RAG, chunking, top-k retrieval, and hallucination
reduction — the same concepts explained above, written as short course
notes.

Every command below was actually run against this app to produce the
"expected response" shown — copy-pasteable and accurate, not
hypothetical.

Ingesting the file's content requires building a JSON body from a
multi-line text file, which is awkward to type by hand. The commands
below use [`jq`](https://jqlang.org/) to build that JSON payload safely
(handles newlines/quotes correctly). Install it first if you don't
have it: `brew install jq` (macOS) or `apt install jq` (Linux).

### 1. Start the application

```bash
cd projects/day1-simple-rag-demo
mvn spring-boot:run
```

**What to explain to students:** this boots an embedded web server on
port 8080. Nothing is ingested yet — the in-memory stores are empty
until a document is posted to `/api/documents/ingest`.

### 2. Health check

```bash
curl http://localhost:8080/api/health
```

**Expected response**
```json
{
  "status": "UP",
  "message": "Day 1 RAG demo is running"
}
```

**What to explain to students:** a health check just proves the server
is up and answering HTTP requests — it says nothing about whether any
documents have been ingested.

### 3. Ingest the sample document

```bash
CONTENT=$(cat src/main/resources/sample-course-notes.txt)
curl -X POST http://localhost:8080/api/documents/ingest \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg id "course-notes" --arg title "GenAI Course Notes" --arg content "$CONTENT" '{documentId: $id, title: $title, content: $content}')"
```

**Expected response**
```json
{
  "documentId": "course-notes",
  "status": "INGESTED",
  "chunksCreated": 5
}
```

**What to explain to students:** this one request runs three RAG
steps at once — the document is stored as-is, split into 5 overlapping
chunks (`rag.chunk.size=500`, `rag.chunk.overlap=100`), and every
chunk is embedded into the vector store. Nothing has been retrieved or
answered yet — ingestion only prepares the data to be searched.

### 4. Search: "What is RAG?"

```bash
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is RAG?",
    "topK": 3
  }'
```

**Expected response** (abbreviated — `chunkText` is the full ~500-character chunk)
```json
{
  "query": "What is RAG?",
  "results": [
    { "chunkText": "...RAG stands for Retrieval-Augmented Generation...", "score": 0.2955, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 1 } },
    { "chunkText": "...RAG grounds answers in real, verifiable information...", "score": 0.2275, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 2 } },
    { "chunkText": "...Top-k retrieval finds the k most relevant chunks...", "score": 0.0854, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 3 } }
  ]
}
```

**What to explain to students:** `/api/search` only retrieves — no
answer is generated here. Notice the top two chunks both score above
`rag.min-score` (0.25) and both actually mention RAG; the third chunk
(top-k retrieval / hallucination content) scores much lower because it
shares far fewer words with the query.

### 5. Search: "How does semantic search work?"

```bash
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How does semantic search work?",
    "topK": 3
  }'
```

**Expected response** (abbreviated)
```json
{
  "query": "How does semantic search work?",
  "results": [
    { "chunkText": "...Semantic search compares a query's vector against stored vectors...", "score": 0.3270, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 1 } },
    { "chunkText": "...Embeddings turn text into vectors...", "score": 0.3266, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 0 } },
    { "chunkText": "...RAG grounds answers in real, verifiable information...", "score": 0.1410, "metadata": { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 2 } }
  ]
}
```

**What to explain to students:** a different query retrieves a
different, appropriately-ranked set of chunks from the *same* stored
document — this is the "search" in semantic search: matching by
meaning-ish (word overlap, for our fake embedding), not by exact
keyword lookup.

### 6. Ask a RAG question: "What is RAG?"

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is RAG?",
    "topK": 3
  }'
```

**Expected response** (abbreviated)
```json
{
  "question": "What is RAG?",
  "answer": "Based on the provided course notes, ...RAG stands for Retrieval-Augmented Generation. RAG answers questions with your own documents. RAG retrieves relevant chunks...",
  "sources": [
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 1, "score": 0.2955 },
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 2, "score": 0.2275 },
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 3, "score": 0.0854 }
  ],
  "retrievedChunks": [ "..." ]
}
```

**What to explain to students:** this is the same retrieval as step 4,
plus one more step — the retrieved chunks were combined with the
question into a prompt (check the console log for the exact text),
and the mock LLM turned that into an answer. `sources` lets you trace
the answer back to exactly which chunk of which document it came from.

### 7. Ask a RAG question: "What are embeddings?"

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are embeddings?",
    "topK": 3
  }'
```

**Expected response** (abbreviated)
```json
{
  "question": "What are embeddings?",
  "answer": "Based on the provided course notes, Embeddings turn text into vectors. Embeddings represent the meaning of text as numbers...",
  "sources": [
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 0, "score": 0.2811 },
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 2, "score": 0.0455 },
    { "documentId": "course-notes", "title": "GenAI Course Notes", "chunkIndex": 1, "score": 0.0422 }
  ],
  "retrievedChunks": [ "..." ]
}
```

**What to explain to students:** the safety net (see above) only
checks the *highest* score among the retrieved chunks — here chunk 0
clears `rag.min-score` at 0.2811, so the LLM call goes ahead and
**all** `topK` retrieved chunks become sources, even though chunks 1
and 2 individually score far lower (~0.04). This is a good moment to
point out the safety net's actual scope: it's a single all-or-nothing
gate on the *best* match, not a per-chunk filter — the weaker chunks
still get handed to the LLM as context (and cited as sources) as long
as at least one strong match was found. A stricter design could filter
weak chunks individually; that's a natural extension exercise.

### 8. Ask an unknown question: "What is Kubernetes?"

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is Kubernetes?",
    "topK": 3
  }'
```

**Expected response**
```json
{
  "question": "What is Kubernetes?",
  "answer": "I don't know from the provided documents.",
  "reason": "No retrieved chunk passed minimum similarity threshold",
  "sources": [],
  "retrievedChunks": [ "..." ]
}
```

**What to explain to students:** the sample document never mentions
Kubernetes, so even the *best* retrieved chunk only scores ~0.21 —
below `rag.min-score` (0.25). The safety net in `RagService` catches
this and returns the safe fallback answer **without ever calling the
LLM**, instead of letting the model guess or hallucinate an answer
about a topic that was never in the documents. This is the entire
point of grounding: an AI that admits "I don't know" is more
trustworthy than one that always sounds confident.

## What's next

This wraps up the Day 1 manual RAG pipeline (ingestion → chunking →
embeddings → vector storage → search → answer generation). Future
lessons can build on this foundation with things like a real embedding
model, a real LLM call, smarter chunking strategies, or persistent
(non in-memory) storage — but those are deliberately out of scope for
this beginner-friendly demo.
