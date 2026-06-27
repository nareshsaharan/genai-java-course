package com.example.aichatbot.controller;

import com.example.aichatbot.dto.ConversationChatRequest;
import com.example.aichatbot.dto.ConversationHistoryResponse;
import com.example.aichatbot.service.ConversationChatService;
import com.example.aichatbot.store.ConversationStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * ConversationChatController – stateful chat with persistent history.
 *
 * Each request includes a conversationId so the service can load previous
 * messages and send them to the model.  This lets the AI answer follow-up
 * questions that reference earlier parts of the conversation.
 *
 * Endpoints:
 *   GET    /api/chat/conversation/stream?conversationId=&question=  → SSE stream
 *   POST   /api/chat/conversation                                    → SSE stream (JSON body)
 *   GET    /api/chat/conversation/history?conversationId=           → message history
 *   DELETE /api/chat/conversation?conversationId=                   → clear history
 */
@RestController
@RequestMapping("/api/chat/conversation")
public class ConversationChatController {

    private final ConversationChatService conversationChatService;
    private final ConversationStore conversationStore;

    public ConversationChatController(
            ConversationChatService conversationChatService,
            ConversationStore conversationStore) {

        this.conversationChatService = conversationChatService;
        this.conversationStore = conversationStore;
    }

    /**
     * Streaming stateful chat via query params — easiest to test with curl.
     *
     * Try it:
     *   curl -N -G "http://localhost:8080/api/chat/conversation/stream" \
     *        --data-urlencode "conversationId=session-1" \
     *        --data-urlencode "question=What is Java?"
     *
     *   # Follow-up (model remembers the previous answer):
     *   curl -N -G "http://localhost:8080/api/chat/conversation/stream" \
     *        --data-urlencode "conversationId=session-1" \
     *        --data-urlencode "question=Can you give an example?"
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String conversationId,
            @RequestParam String question) {

        return conversationChatService.chat(conversationId, question);
    }

    /**
     * Streaming stateful chat via JSON body — preferred for frontend clients.
     *
     * Try it:
     *   curl -N -X POST http://localhost:8080/api/chat/conversation \
     *        -H "Content-Type: application/json" \
     *        -d '{"conversationId":"session-1","message":"What is Java?"}'
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ConversationChatRequest request) {
        return conversationChatService.chat(request.conversationId(), request.message());
    }

    /**
     * Returns the stored message history for a conversation (up to 10 messages).
     *
     * Try it:
     *   curl "http://localhost:8080/api/chat/conversation/history?conversationId=session-1"
     */
    @GetMapping("/history")
    public ConversationHistoryResponse history(@RequestParam String conversationId) {
        return new ConversationHistoryResponse(
                conversationId,
                conversationStore.getMessages(conversationId)
        );
    }

    /**
     * Wipes the history for a conversation so the model starts fresh.
     *
     * Try it:
     *   curl -X DELETE "http://localhost:8080/api/chat/conversation?conversationId=session-1"
     */
    @DeleteMapping
    public ResponseEntity<String> clear(@RequestParam String conversationId) {
        conversationStore.clearConversation(conversationId);
        return ResponseEntity.ok("Conversation [" + conversationId + "] cleared.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body("Something went wrong: " + ex.getMessage());
    }
}
