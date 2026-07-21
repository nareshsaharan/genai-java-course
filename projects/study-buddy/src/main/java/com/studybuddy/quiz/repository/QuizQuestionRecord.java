package com.studybuddy.quiz.repository;

import java.util.List;
import java.util.UUID;

/** Row shape of {@code quiz_questions}. */
public record QuizQuestionRecord(
        UUID id,
        UUID quizId,
        int questionIndex,
        String questionText,
        List<String> options,
        int correctOptionIndex,
        UUID sourceChunkId
) {
}
