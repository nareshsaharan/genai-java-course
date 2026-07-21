package com.studybuddy.flashcard;

/**
 * Raw shape returned by {@link FlashcardGenerator}: just what the model
 * produces. Id, topic, difficulty and source chunk ids are attached
 * afterwards by {@link FlashcardService}, since the model has no business
 * inventing them.
 */
public record GeneratedFlashcard(String question, String answer) {
}
