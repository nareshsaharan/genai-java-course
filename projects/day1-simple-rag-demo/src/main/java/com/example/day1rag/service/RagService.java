package com.example.day1rag.service;

import com.example.day1rag.model.RagRequest;
import com.example.day1rag.model.RagResponse;
import com.example.day1rag.model.SearchRequest;
import com.example.day1rag.model.SearchResponse;
import com.example.day1rag.model.SearchResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Step 6 of RAG: "answer generation" — combine retrieved chunks with
 * the question and ask an LLM to answer, using ONLY that context.
 *
 * Flow:
 *   1. Take the question.
 *   2. Retrieve the topK most relevant chunks (RetrievalService — the
 *      same retrieval step used by /api/search).
 *   3. Check the highest similarity score among those chunks. If it's
 *      below rag.min-score, the retrieved chunks aren't actually
 *      relevant — skip the LLM entirely and return a safe "I don't
 *      know" answer instead of risking a made-up response.
 *   4. Otherwise, build a final prompt from those chunks + the question
 *      and send it to LlmService (MockLlmService for Day 1).
 *   5. Return the answer, the retrieved chunks, and a citation for every
 *      retrieved chunk (documentId, title, chunkIndex, score) — that's
 *      what lets students (and, in production, real users) verify
 *      exactly where an answer came from. See the README for why this
 *      "source traceability" matters.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String NO_ANSWER_MESSAGE = "I don't know from the provided documents.";
    private static final String LOW_SCORE_REASON = "No retrieved chunk passed minimum similarity threshold";

    // {{retrieved_chunks}} and {{question}} get filled in for every request.
    private static final String PROMPT_TEMPLATE = """
            Use only the context below to answer the question.

            Context:
            %s

            Question:
            %s

            Rules:
            - Answer only from the context.
            - If answer is not present in the context, say:
              "I don't know from the provided documents."
            """;

    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final double minScore;

    public RagService(RetrievalService retrievalService,
                       LlmService llmService,
                       @Value("${rag.min-score:0.25}") double minScore) {
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.minScore = minScore;
    }

    public RagResponse ask(RagRequest request) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(request.getTopK());

        SearchResponse searchResponse = retrievalService.search(searchRequest);
        List<SearchResult> retrievedChunks = searchResponse.getResults();

        double highestScore = retrievedChunks.stream()
                .mapToDouble(SearchResult::getScore)
                .max()
                .orElse(0.0);

        if (highestScore < minScore) {
            // The best match we found still isn't similar enough to
            // trust — don't call the LLM at all, just play it safe.
            log.info("Highest retrieved score {} is below rag.min-score {} — skipping LLM call", highestScore, minScore);
            return new RagResponse(request.getQuestion(), NO_ANSWER_MESSAGE, LOW_SCORE_REASON,
                    Collections.emptyList(), retrievedChunks);
        }

        String context = buildContext(retrievedChunks);
        String prompt = PROMPT_TEMPLATE.formatted(context, request.getQuestion());

        // Print the final prompt so students can see exactly what gets
        // sent to the "LLM" — this is the whole point of RAG: grounding
        // the model's answer in real, retrieved context.
        log.info("Final prompt sent to LLM:\n{}", prompt);

        String answer = llmService.generateAnswer(prompt);
        List<Map<String, Object>> sources = buildSources(retrievedChunks);

        return new RagResponse(request.getQuestion(), answer, null, sources, retrievedChunks);
    }

    /**
     * Joins every retrieved chunk's text into one block, separated by
     * blank lines, ready to drop into the prompt template.
     */
    private String buildContext(List<SearchResult> retrievedChunks) {
        return retrievedChunks.stream()
                .map(SearchResult::getChunkText)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Builds one citation per retrieved chunk: exactly which document,
     * title, chunk, and how confident the match was (its score). This
     * is what lets anyone reading the response trace the answer back
     * to its exact source, instead of just trusting it blindly.
     */
    private List<Map<String, Object>> buildSources(List<SearchResult> retrievedChunks) {
        return retrievedChunks.stream()
                .map(result -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("documentId", result.getMetadata().get("documentId"));
                    source.put("title", result.getMetadata().get("title"));
                    source.put("chunkIndex", result.getMetadata().get("chunkIndex"));
                    source.put("score", result.getScore());
                    return source;
                })
                .collect(Collectors.toList());
    }
}
