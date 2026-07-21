package com.studybuddy.mcp.dto;

import java.util.List;

public record StudyPlanToolResult(
        List<WeakTopicToolResult> weakTopics,
        RecommendationToolResult recommendation,
        String summary
) {
}
