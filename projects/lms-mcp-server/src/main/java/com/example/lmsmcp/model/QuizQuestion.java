package com.example.lmsmcp.model;

import java.util.List;

/**
 * A single multiple-choice question that lives inside a QuizResponse.
 */
public record QuizQuestion(
        String question,
        List<String> options,
        String correctAnswer,
        String explanation
) {
}
