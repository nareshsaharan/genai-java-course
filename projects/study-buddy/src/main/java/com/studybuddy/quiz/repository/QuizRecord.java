package com.studybuddy.quiz.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A quiz plus its questions — the shape needed to score a submission. */
public record QuizRecord(
        UUID id,
        String topic,
        String difficulty,
        Instant createdAt,
        List<QuizQuestionRecord> questions
) {
}
