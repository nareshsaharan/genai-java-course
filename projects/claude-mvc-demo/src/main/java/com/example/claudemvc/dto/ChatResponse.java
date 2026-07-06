package com.example.claudemvc.dto;

/**
 * What the server sends back from POST /api/chat.
 */
public record ChatResponse(String reply) {
}
