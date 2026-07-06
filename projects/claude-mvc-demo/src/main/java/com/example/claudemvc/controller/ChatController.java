package com.example.claudemvc.controller;

import com.example.claudemvc.dto.ChatRequest;
import com.example.claudemvc.dto.ChatResponse;
import com.example.claudemvc.service.ClaudeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller layer: exposes the HTTP endpoint and translates between
 * DTOs and the service layer. Contains no Claude/Anthropic-specific code.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ClaudeService claudeService;

    public ChatController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    /**
     * POST /api/chat
     * Body:     {"message": "Explain Java inheritance in simple words."}
     * Response: {"reply": "..."}
     */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = claudeService.askClaude(request.message());
        return new ChatResponse(reply);
    }
}
