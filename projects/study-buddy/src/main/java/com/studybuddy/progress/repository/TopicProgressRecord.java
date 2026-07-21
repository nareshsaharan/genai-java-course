package com.studybuddy.progress.repository;

import java.time.Instant;

/** Row shape of {@code topic_progress}. */
public record TopicProgressRecord(
        String topic,
        int correctCount,
        int totalCount,
        double accuracy,
        Instant lastAttemptAt,
        Instant lastRecommendedAt,
        Instant updatedAt
) {
}
