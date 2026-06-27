package com.example.aichatbot.service;

import com.example.aichatbot.guard.CostGuard;
import com.example.aichatbot.model.ConversationMessage;
import com.example.aichatbot.store.ConversationStore;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ConversationChatService – stateful chat that remembers previous messages.
 *
 * What makes this different from ChatService?
 * ChatService treats every question as brand-new.  This service keeps a
 * per-conversation history so the AI can answer follow-up questions like
 * "can you explain that in simpler terms?" and actually know what "that" refers to.
 *
 * How memory works here:
 *   1. We save the user's message to ConversationStore.
 *   2. We load the full history for this conversation.
 *   3. We send the entire history to OpenAI so the model has context.
 *   4. As the reply streams back, we show it to the user in real time.
 *   5. Once the stream finishes, we save the assistant's reply to history.
 *
 * What is a conversationId?
 * A unique string (e.g. "user-abc-session-1") that ties messages together.
 * Two users with different conversationIds never see each other's messages —
 * this is called session isolation.
 *
 * Why pass instructions separately?
 * The system prompt (instructions) sets the AI's persona/rules.  We pass it
 * via ResponseCreateParams.instructions() instead of putting it in the message
 * history.  This means it never gets saved to ConversationStore and never
 * counts against our 10-message history window.
 */
@Service
public class ConversationChatService {

    private static final Logger log = LoggerFactory.getLogger(ConversationChatService.class);

    private static final String SYSTEM_PROMPT =
        "You are a helpful and friendly AI assistant. " +
        "Answer clearly and concisely. When you don't know something, say so.";

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    private final CostGuard costGuard;
    private final ConversationStore store;
    private final Optional<OpenAIClient> openAIClient;

    public ConversationChatService(
            CostGuard costGuard,
            ConversationStore store,
            @Autowired(required = false) OpenAIClient openAIClient) {

        this.costGuard = costGuard;
        this.store = store;
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Streams a reply for the given message, maintaining conversation history.
     *
     * @param conversationId unique session identifier (caller generates this)
     * @param userMessage    what the user typed
     * @return Flux that emits text chunks as they arrive from the model
     */
    public Flux<String> chat(String conversationId, String userMessage) {

        // Step 1 – reject blank or oversized messages before touching the API
        costGuard.validate(userMessage);

        // Step 2 – persist the user's message so it becomes part of history
        store.addMessage(conversationId, new ConversationMessage("user", userMessage, Instant.now()));

        // Step 3 – load the full history (includes the message we just saved)
        List<ConversationMessage> history = store.getMessages(conversationId);

        log.info("[{}] Sending {} message(s) to model (mock={})", conversationId, history.size(), mockMode);

        // Step 4 – stream the reply
        Flux<String> responseStream = mockMode
                ? streamMockReply(conversationId, userMessage)
                : streamFromOpenAI(conversationId, history);

        // Step 5 – once the stream completes, save the assistant's full reply
        return collectAndSave(conversationId, responseStream);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * MOCK MODE: returns a fake streaming reply without calling OpenAI.
     * Useful for local development when you don't have an API key.
     */
    private Flux<String> streamMockReply(String conversationId, String userMessage) {
        String fakeReply =
            "[MOCK] You asked: \"" + userMessage + "\". " +
            "This is a simulated reply for conversation [" + conversationId + "]. " +
            "Set app.mock-mode=false and add OPENAI_API_KEY to use real AI.";

        // Split on whitespace boundaries so each word arrives separately
        String[] words = fakeReply.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(words);
    }

    /**
     * REAL MODE: converts stored history into SDK input items and calls
     * the OpenAI Responses API with streaming.
     *
     * Key steps:
     *   a) Turn each ConversationMessage into an EasyInputMessage (SDK type).
     *   b) Wrap each EasyInputMessage in a ResponseInputItem.
     *   c) Pass the list + system instructions to ResponseCreateParams.
     *   d) Bridge the blocking SDK stream to a reactive Flux.
     */
    private Flux<String> streamFromOpenAI(String conversationId, List<ConversationMessage> history) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // a) Convert our domain messages into SDK EasyInputMessage objects.
        //    We only recognise "user" and "assistant" roles here — system
        //    prompt is passed separately via .instructions() below.
        List<ResponseInputItem> inputItems = history.stream()
            .map(msg -> {
                EasyInputMessage.Role sdkRole = "assistant".equalsIgnoreCase(msg.role())
                    ? EasyInputMessage.Role.ASSISTANT
                    : EasyInputMessage.Role.USER;

                EasyInputMessage easyMessage = EasyInputMessage.builder()
                    .role(sdkRole)
                    .content(msg.content())
                    .build();

                // b) Wrap in ResponseInputItem so the SDK can accept it
                return ResponseInputItem.ofEasyInputMessage(easyMessage);
            })
            .toList();

        // c) Build the request:
        //    - inputOfResponse() sends the full message list
        //    - instructions() is the system prompt (NOT stored in history)
        ResponseCreateParams params = ResponseCreateParams.builder()
            .model(model)
            .inputOfResponse(inputItems)
            .instructions(SYSTEM_PROMPT)
            .build();

        // d) Bridge the blocking SDK iterator to a reactive Flux.
        //    subscribeOn(boundedElastic) runs the blocking code on a thread
        //    pool so it doesn't stall Spring WebFlux's event loop.
        return Flux.<String>create(sink -> {
            try (var stream = client.responses().createStreaming(params)) {

                stream.stream()
                    .filter(ResponseStreamEvent::isOutputTextDelta)
                    .map(event -> event.outputTextDelta()
                                       .map(ResponseTextDeltaEvent::delta)
                                       .orElse(""))
                    .filter(delta -> !delta.isEmpty())
                    .forEach(sink::next);

                sink.complete();

            } catch (Exception ex) {
                log.error("[{}] Streaming error: {}", conversationId, ex.getMessage());
                sink.error(ex);
            }
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Taps the live stream to accumulate all chunks into one string, then
     * saves the complete assistant reply to history when the stream finishes.
     *
     * doOnNext  – called for every chunk while streaming (we collect it)
     * doOnComplete – called once at the very end (we save to store)
     */
    private Flux<String> collectAndSave(String conversationId, Flux<String> source) {
        StringBuilder accumulator = new StringBuilder();
        return source
            .doOnNext(accumulator::append)
            .doOnComplete(() -> {
                String fullReply = accumulator.toString();
                if (!fullReply.isBlank()) {
                    store.addMessage(
                        conversationId,
                        new ConversationMessage("assistant", fullReply, Instant.now())
                    );
                    log.info("[{}] Assistant reply saved ({} chars)", conversationId, fullReply.length());
                }
            });
    }
}
