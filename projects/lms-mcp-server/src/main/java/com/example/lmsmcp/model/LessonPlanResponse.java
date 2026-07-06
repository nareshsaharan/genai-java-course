package com.example.lmsmcp.model;

import java.util.List;

/**
 * Response for the "getLessonPlan" tool.
 * Describes how a topic should be taught: the overall goal, the
 * sub-topics to cover, what a student should be able to do afterwards,
 * how long it should take, and where to find more material.
 */
public record LessonPlanResponse(
        String topic,
        String objective,
        List<String> subTopics,
        List<String> learningOutcomes,
        String estimatedDuration,
        List<String> resources
) implements ToolOutcome {
}
