package com.example.aichatbot.controller;

import com.example.aichatbot.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * ChatController – the HTTP layer.
 *
 * Exposes one endpoint:
 *
 *   GET /chat?message=Hello
 *
 * The response is Server-Sent Events (SSE): a stream of text chunks that
 * the browser / curl receives word-by-word as they are generated.
 *
 * Try it:
 *   curl -N "http://localhost:8080/chat?message=Hello"
 *
 * Lesson concepts: REST controller, query params, SSE, reactive return types.
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Streaming chat endpoint.
     *
     * MediaType.TEXT_EVENT_STREAM_VALUE tells Spring WebFlux to keep the HTTP
     * connection open and push each Flux element as an SSE "data:" line.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message) {
        return chatService.chat(message);
    }

    /**
     * Global error handler for this controller.
     *
     * If CostGuard or any other component throws an IllegalArgumentException,
     * we return 400 Bad Request with a readable message instead of a stack trace.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

    /**
     * Catch-all for unexpected errors (e.g., OpenAI is down).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericError(Exception ex) {
        return ResponseEntity.internalServerError()
               .body("Something went wrong: " + ex.getMessage());
    }
}
