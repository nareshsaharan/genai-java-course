package com.studybuddy.quiz.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerSubmission(

        @NotNull(message = "questionId is required")
        UUID questionId,

        @Min(value = 0, message = "selectedOptionIndex must be at least 0")
        int selectedOptionIndex
) {
}
