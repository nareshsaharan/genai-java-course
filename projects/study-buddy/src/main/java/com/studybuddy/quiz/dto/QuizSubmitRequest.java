package com.studybuddy.quiz.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record QuizSubmitRequest(

        @NotEmpty(message = "answers must not be empty")
        @Valid
        List<QuizAnswerSubmission> answers
) {
}
