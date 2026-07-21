package com.studybuddy.quiz.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Row shape of {@code quiz_attempts}, paired with its answers. */
public record QuizAttemptRecord(
        UUID id,
        UUID quizId,
        String topic,
        int correctCount,
        int totalCount,
        Instant submittedAt,
        List<QuizAnswerRecord> answers
) {
}
