package com.studybuddy.mcp.dto;

public record SaveQuizResultToolResult(
        String topic,
        int correctAnswers,
        int totalQuestions,
        double updatedAccuracy,
        String message
) {
}
