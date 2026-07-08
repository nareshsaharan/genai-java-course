package com.example.day1rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The JSON body returned by POST /api/rag/ask.
 *
 * - "answer" comes from the LLM (MockLlmService for now), grounded in
 *   the retrieved chunks.
 * - "reason" is only present when we deliberately skipped calling the
 *   LLM (e.g. the best retrieved chunk wasn't similar enough) — see
 *   RagService for the minimum-score safety check. On a normal,
 *   successful answer this field is left out of the JSON entirely.
 * - "sources" is one citation per retrieved chunk — documentId, title,
 *   chunkIndex, and score — so the answer can be traced back to
 *   exactly where it came from. See the README for why this matters.
 * - "retrievedChunks" is the full detail (same shape as SearchResult,
 *   including the chunk's raw text) so students can see exactly what
 *   was retrieved, even when the answer ends up being
 *   "I don't know from the provided documents."
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagResponse {

    private final String question;
    private final String answer;
    private final String reason;
    private final List<Map<String, Object>> sources;
    private final List<SearchResult> retrievedChunks;

    public RagResponse(String question, String answer, String reason,
                        List<Map<String, Object>> sources,
                        List<SearchResult> retrievedChunks) {
        this.question = question;
        this.answer = answer;
        this.reason = reason;
        this.sources = sources;
        this.retrievedChunks = retrievedChunks;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getReason() {
        return reason;
    }

    public List<Map<String, Object>> getSources() {
        return sources;
    }

    public List<SearchResult> getRetrievedChunks() {
        return retrievedChunks;
    }
}
