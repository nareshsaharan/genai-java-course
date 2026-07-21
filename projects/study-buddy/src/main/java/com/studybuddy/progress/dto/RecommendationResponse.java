package com.studybuddy.progress.dto;

public record RecommendationResponse(
        String topic,
        String reason,
        double accuracy,
        int totalCount
) {
}
