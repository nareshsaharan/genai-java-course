package com.example.claudemvc.service;

/**
 * Result of a two-turn conversation carried out via {@link ClaudeService}.
 *
 * <p>This is an internal service-layer type (not an HTTP DTO) - the
 * controller maps it into whatever response shape the client needs.
 */
public record ConversationResult(String firstReply, String secondReply) {
}
