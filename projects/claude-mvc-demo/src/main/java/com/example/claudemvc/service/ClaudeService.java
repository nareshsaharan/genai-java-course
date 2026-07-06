package com.example.claudemvc.service;

import java.util.function.Consumer;

/**
 * Service layer contract: given a user's message, return Claude's reply.
 *
 * <p>The controller only depends on this interface - it never talks to the
 * Anthropic SDK directly. This keeps HTTP concerns and "how do we get an
 * answer from Claude" concerns separate.
 */
public interface ClaudeService {

    String askClaude(String userMessage);

    /**
     * Sends {@code firstMessage} on its own, then sends {@code secondMessage}
     * as a follow-up that includes the first message and Claude's first
     * reply as history - so Claude can use context from the first turn while
     * answering the second.
     *
     * <p>Claude itself does not remember anything between calls; this method
     * is what makes the "memory" happen, by resending everything so far on
     * the second request.
     */
    ConversationResult continueConversation(String firstMessage, String secondMessage);

    /**
     * Sends {@code userMessage} to Claude using the STREAMING API and invokes
     * {@code onTextChunk} once for every small piece of text as it arrives,
     * instead of waiting for the whole reply to be ready.
     *
     * <p>Using a callback here (instead of returning one big String) keeps
     * the Anthropic SDK's streaming types out of the controller layer - the
     * controller only needs to know "I get called with text chunks", not how
     * Claude's streaming API actually works.
     */
    void streamClaude(String userMessage, Consumer<String> onTextChunk);
}
