package com.studybuddy.flashcard.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Row shape of {@code flashcards}, paired with the chunk ids it was generated from. */
public record FlashcardRecord(
        UUID id,
        String topic,
        String difficulty,
        String question,
        String answer,
        List<UUID> sourceChunkIds,
        Instant createdAt
) {
}
