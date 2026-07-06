package com.example.claudemvc.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What a client sends to POST /api/demo/conversation-history.
 *
 * <p>{@code firstMessage} is sent on its own (CALL 1). {@code secondMessage}
 * is then sent as a follow-up (CALL 2 WITH HISTORY) that includes
 * {@code firstMessage} and Claude's first reply as context.
 *
 * <p>Example:
 * <pre>
 * {
 *   "firstMessage": "My name is Ankur.",
 *   "secondMessage": "What is my name?"
 * }
 * </pre>
 */
public record ConversationHistoryRequest(

        @NotBlank(message = "firstMessage must not be blank")
        String firstMessage,

        @NotBlank(message = "secondMessage must not be blank")
        String secondMessage

) {
}
