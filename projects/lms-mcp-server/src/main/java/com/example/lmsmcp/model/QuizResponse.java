package com.example.lmsmcp.model;

import java.util.List;

/**
 * Response for the "generateQuiz" tool.
 * A quiz is just a topic, its difficulty, and the list of questions
 * generated for it.
 */
public record QuizResponse(
        String topic,
        String difficulty,
        List<QuizQuestion> questions
) implements ToolOutcome {
}
