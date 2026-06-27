package com.example.aichatbot.controller;

import com.example.aichatbot.service.ChatService;
import com.example.aichatbot.service.ConversationChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * DemoController – simplified endpoints for live demos and classroom walkthroughs.
 *
 * These mirror the stateless and stateful endpoints but use GET with query params
 * so they can be pasted directly into a browser address bar or curl without
 * needing JSON bodies or POST requests.
 *
 * Endpoints:
 *   GET /api/demo/stateless?question=
 *   GET /api/demo/stateful?conversationId=&question=
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final ChatService chatService;
    private final ConversationChatService conversationChatService;

    public DemoController(ChatService chatService, ConversationChatService conversationChatService) {
        this.chatService = chatService;
        this.conversationChatService = conversationChatService;
    }

    /**
     * Stateless demo — no memory, just answer this one question.
     *
     * Try it:
     *   curl -N -G "http://localhost:8080/api/demo/stateless" \
     *        --data-urlencode "question=What is generative AI?"
     */
    @GetMapping(value = "/stateless", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stateless(@RequestParam String question) {
        return chatService.chat(question);
    }

    /**
     * Stateful demo — remembers previous questions under the same conversationId.
     *
     * Try it (ask two questions in a row with the same conversationId):
     *   curl -N -G "http://localhost:8080/api/demo/stateful" \
     *        --data-urlencode "conversationId=demo-1" \
     *        --data-urlencode "question=My name is Alice"
     *
     *   curl -N -G "http://localhost:8080/api/demo/stateful" \
     *        --data-urlencode "conversationId=demo-1" \
     *        --data-urlencode "question=What is my name?"
     */
    @GetMapping(value = "/stateful", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stateful(
            @RequestParam String conversationId,
            @RequestParam String question) {

        return conversationChatService.chat(conversationId, question);
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
