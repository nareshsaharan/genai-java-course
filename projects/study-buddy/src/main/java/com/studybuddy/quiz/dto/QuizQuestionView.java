package com.studybuddy.quiz.dto;

import java.util.List;
import java.util.UUID;

/** Pre-submission view of a question: deliberately omits the correct answer. */
public record QuizQuestionView(
        UUID questionId,
        int questionIndex,
        String questionText,
        List<String> options
) {
}
