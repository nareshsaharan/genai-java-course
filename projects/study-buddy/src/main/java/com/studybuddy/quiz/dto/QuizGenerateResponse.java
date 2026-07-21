package com.studybuddy.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizGenerateResponse(
        UUID quizId,
        String topic,
        List<QuizQuestionView> questions
) {
}
