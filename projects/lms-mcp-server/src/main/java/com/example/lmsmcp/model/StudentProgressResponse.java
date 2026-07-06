package com.example.lmsmcp.model;

import java.util.List;

/**
 * Response for the "checkStudentProgress" tool.
 * Shows what a student has already completed, what they're studying
 * right now, and how far along they are overall.
 */
public record StudentProgressResponse(
        String studentId,
        String studentName,
        List<String> completedTopics,
        String currentTopic,
        double completionPercentage,
        String lastActivityDate
) implements ToolOutcome {
}
