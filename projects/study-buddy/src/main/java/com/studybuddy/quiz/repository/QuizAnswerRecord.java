package com.studybuddy.quiz.repository;

import java.util.UUID;

/** Row shape of {@code quiz_answers}. */
public record QuizAnswerRecord(
        UUID id,
        UUID attemptId,
        UUID questionId,
        int selectedOptionIndex,
        boolean correct
) {
}
