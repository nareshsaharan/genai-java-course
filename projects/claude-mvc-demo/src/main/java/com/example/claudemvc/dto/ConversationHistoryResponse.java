package com.example.claudemvc.dto;

/**
 * What POST /api/demo/conversation-history returns.
 *
 * <p>{@code secondReply} should correctly use context from {@code firstReply}
 * / the first message, because the full history was resent on the second
 * call - contrast with {@link StatelessDemoResponse}, where it is not.
 */
public record ConversationHistoryResponse(
        String firstReply,
        String secondReply,
        String explanation
) {
}
