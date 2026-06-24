package com.example.aichatbot.guard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CostGuard – rejects messages that are too long before they hit the API.
 *
 * Why this matters: OpenAI charges per token.  A single runaway request from
 * a student can rack up a large bill.  This guard is the first line of defence.
 *
 * Lesson concept: input validation at the service boundary.
 */
@Component
public class CostGuard {

    private final int maxInputChars;

    public CostGuard(@Value("${app.cost-guard.max-input-chars:500}") int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    /**
     * Throws an exception if the message is blank or exceeds the character limit.
     */
    public void validate(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be empty.");
        }
        if (message.length() > maxInputChars) {
            throw new IllegalArgumentException(
                "Message too long: %d chars (max %d). Please shorten your question."
                    .formatted(message.length(), maxInputChars)
            );
        }
    }
}
