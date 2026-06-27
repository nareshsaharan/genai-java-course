package com.example.aichatbot.controller;

import com.example.aichatbot.dto.ChatRequest;
import com.example.aichatbot.dto.ChatResponse;
import com.example.aichatbot.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * StatelessChatController – no memory between requests.
 *
 * Every call is independent: the model receives only the current question.
 * Good for one-shot lookups ("what is Java?") where history doesn't matter.
 *
 * Endpoints:
 *   GET  /api/chat/stream?question=   → streaming SSE (word-by-word)
 *   POST /api/chat/stateless          → single JSON response (full answer)
 *
 * Lesson concept: SSE vs REST JSON — same service, two response styles.
 */
@RestController
@RequestMapping("/api/chat")
public class StatelessChatController {

    private final ChatService chatService;

    public StatelessChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Streaming endpoint — keeps the HTTP connection open and pushes each
     * text chunk as a Server-Sent Event (SSE) "data:" line.
     *
     * Try it:
     *   curl -N -G "http://localhost:8080/api/chat/stream" \
     *        --data-urlencode "question=What is generative AI"
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String question) {
        return chatService.chat(question);
    }

    /**
     * Non-streaming endpoint — collects the full answer, then returns it as JSON.
     * Simpler to test with Postman or curl but slower to first byte.
     *
     * Try it:
     *   curl -X POST http://localhost:8080/api/chat/stateless \
     *        -H "Content-Type: application/json" \
     *        -d '{"message":"What is Java?"}'
     */
    @PostMapping("/stateless")
    public Mono<ChatResponse> stateless(@RequestBody ChatRequest request) {
        return chatService.chat(request.message())
                .collect(StringBuilder::new, StringBuilder::append)
                .map(sb -> new ChatResponse(sb.toString()));
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
