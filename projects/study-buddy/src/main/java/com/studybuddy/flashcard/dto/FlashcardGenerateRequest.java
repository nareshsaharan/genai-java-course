package com.studybuddy.flashcard.dto;

import com.studybuddy.flashcard.Difficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FlashcardGenerateRequest(

        @NotBlank(message = "topic must not be blank")
        String topic,

        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 20, message = "count must be at most 20")
        int count,

        @NotNull(message = "difficulty is required")
        Difficulty difficulty
) {
}
