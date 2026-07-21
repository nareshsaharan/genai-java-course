package com.studybuddy.mcp.dto;

public record RecommendationToolResult(
        boolean available,
        String topic,
        String reason,
        double accuracy,
        int totalCount
) {
}
