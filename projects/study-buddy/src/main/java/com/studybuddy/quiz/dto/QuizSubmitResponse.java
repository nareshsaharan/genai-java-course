package com.studybuddy.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizSubmitResponse(
        UUID attemptId,
        String topic,
        int correctCount,
        int totalCount,
        double accuracy,
        List<QuizAnswerResult> results
) {
}
