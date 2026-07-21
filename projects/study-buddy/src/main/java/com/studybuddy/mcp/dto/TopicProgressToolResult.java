package com.studybuddy.mcp.dto;

public record TopicProgressToolResult(
        String topic,
        boolean found,
        int correctCount,
        int totalCount,
        double accuracy,
        String classification
) {
}
