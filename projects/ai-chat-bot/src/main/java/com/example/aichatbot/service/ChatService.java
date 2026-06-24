package com.example.aichatbot.service;

import com.example.aichatbot.cache.ResponseCache;
import com.example.aichatbot.guard.CostGuard;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

/**
 * ChatService – the brain of the application.
 *
 * Responsibilities:
 *   1. Validate input via CostGuard.
 *   2. Check the local cache for a previous answer.
 *   3a. MOCK MODE  → stream a fake reply without touching OpenAI.
 *   3b. REAL MODE  → call the OpenAI Responses API with streaming enabled.
 *   4. Cache the full answer for future identical questions.
 *
 * Returns a Flux<String> – a reactive stream of text chunks.
 * Each chunk is one piece of the reply (a word, punctuation, etc.).
 *
 * Lesson concepts: reactive streams, Flux, blocking → reactive bridge,
 *                  Optional, dependency injection, @Value config.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // Injected from application.properties
    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private final CostGuard costGuard;
    private final ResponseCache cache;

    // OpenAIClient is Optional because it won't exist when mock-mode=true
    private final Optional<OpenAIClient> openAIClient;

    public ChatService(
            CostGuard costGuard,
            ResponseCache cache,
            // Spring injects null when the bean doesn't exist; we wrap it safely
            @Autowired(required = false) OpenAIClient openAIClient) {

        this.costGuard = costGuard;
        this.cache = cache;
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Streams an AI reply for the given user message.
     *
     * @param message The user's question / prompt.
     * @return A Flux that emits text chunks as they arrive.
     */
    public Flux<String> chat(String message) {
        // Step 1 – reject bad input early
        costGuard.validate(message);

        // Step 2 – check local cache first (saves money + latency)
        Optional<String> cached = cache.get(message);
        if (cached.isPresent()) {
            log.info("Cache HIT for: {}", message);
            return streamFromCache(cached.get());
        }

        log.info("Cache MISS – calling {} mode for: {}", mockMode ? "MOCK" : "REAL", message);

        // Step 3 – route to mock or real implementation
        Flux<String> responseStream = mockMode
                ? streamMockReply(message)
                : streamFromOpenAI(message);

        // Step 4 – after the stream finishes, save the full answer to cache
        return collectAndCache(message, responseStream);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * MOCK MODE: breaks a fake sentence into words and emits them one by one
     * with a small delay, so the UI looks like real streaming.
     */
    private Flux<String> streamMockReply(String message) {
        String fakeReply =
            "[MOCK] Hello! You asked: \"" + message + "\". " +
            "This is a simulated streaming response. " +
            "Set app.mock-mode=false and add your OPENAI_API_KEY to use real AI.";

        // Split into words, then emit each word + space with a 60 ms gap
        String[] words = fakeReply.split("(?<=\\s)|(?=\\s)"); // keep spaces
        return Flux.fromArray(words)
                   .delayElements(Duration.ofMillis(60));
    }

    /**
     * REAL MODE: calls the OpenAI Responses API with streaming.
     *
     * Key concept – bridging blocking code to reactive:
     *   The OpenAI SDK uses a regular Java Stream (blocking).
     *   Flux.create() + subscribeOn(boundedElastic) runs it on a thread pool
     *   so it doesn't block the reactive event loop.
     */
    private Flux<String> streamFromOpenAI(String message) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException("OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // Build the Responses API request
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(message)
                .build();

        // Flux.create gives us a FluxSink we can push chunks into
        return Flux.<String>create(sink -> {
            try (var streamResponse = client.responses().createStreaming(params)) {

                streamResponse.stream()
                    // isOutputTextDelta() is true only for text content chunks
                    .filter(ResponseStreamEvent::isOutputTextDelta)
                    // outputTextDelta() gives us Optional<ResponseTextDeltaEvent>
                    .map(event -> event.outputTextDelta()
                                       .map(ResponseTextDeltaEvent::delta)
                                       .orElse(""))
                    .filter(delta -> !delta.isEmpty())
                    .forEach(sink::next);   // push each chunk downstream

                sink.complete();            // signal end of stream

            } catch (Exception ex) {
                sink.error(ex);            // propagate errors to the caller
            }
        })
        // Run on a thread pool so the blocking SDK call doesn't stall WebFlux
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Re-streams a previously cached full answer word-by-word.
     * Delivers instantly (no delay) because the answer is already local.
     */
    private Flux<String> streamFromCache(String fullAnswer) {
        String[] words = fullAnswer.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(words);
    }

    /**
     * Taps the live stream, accumulates all chunks, then writes the full text
     * to the cache once the stream completes.
     */
    private Flux<String> collectAndCache(String question, Flux<String> source) {
        StringBuilder accumulator = new StringBuilder();
        return source
            .doOnNext(accumulator::append)           // collect each chunk
            .doOnComplete(() ->                       // called once, at the end
                cache.put(question, accumulator.toString())
            );
    }
}
