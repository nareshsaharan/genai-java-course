package com.studybuddy.quiz.dto;

import java.util.UUID;

/** Post-submission view: correct answer is now revealed. */
public record QuizAnswerResult(
        UUID questionId,
        int selectedOptionIndex,
        int correctOptionIndex,
        boolean correct
) {
}
