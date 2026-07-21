package com.studybuddy.flashcard.dto;

import java.util.List;
import java.util.UUID;

import com.studybuddy.flashcard.Difficulty;

/** API-facing flashcard: id assigned and metadata attached by our service, not the model. */
public record Flashcard(
        UUID id,
        String question,
        String answer,
        String topic,
        Difficulty difficulty,
        List<UUID> sourceChunkIds
) {
}
