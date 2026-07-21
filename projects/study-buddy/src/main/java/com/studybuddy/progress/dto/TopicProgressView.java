package com.studybuddy.progress.dto;

import java.time.Instant;

import com.studybuddy.progress.TopicClassification;

public record TopicProgressView(
        String topic,
        int correctCount,
        int totalCount,
        double accuracy,
        Instant lastAttemptAt,
        TopicClassification classification
) {
}
