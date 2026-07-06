package com.example.claudemvc.dto;

/**
 * What GET /api/demo/stateless returns.
 *
 * <p>{@code call1Reply} and {@code call2WithoutHistoryReply} come from two
 * completely independent calls to Claude, so you can compare them and see
 * that the second call has no memory of the first.
 */
public record StatelessDemoResponse(
        String call1Reply,
        String call2WithoutHistoryReply,
        String explanation
) {
}
