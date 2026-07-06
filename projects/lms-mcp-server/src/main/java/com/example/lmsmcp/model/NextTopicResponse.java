package com.example.lmsmcp.model;

/**
 * Response for the "recommendNextTopic" tool.
 * Tells the client what a student should study next, why that topic
 * was chosen, and at what difficulty level.
 */
public record NextTopicResponse(
        String studentId,
        String recommendedTopic,
        String reason,
        String suggestedDifficulty
) implements ToolOutcome {
}
