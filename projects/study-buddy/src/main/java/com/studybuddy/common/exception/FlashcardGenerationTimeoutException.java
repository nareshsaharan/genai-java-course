package com.studybuddy.common.exception;

/** Thrown when the Claude model call times out while generating flashcards. */
public class FlashcardGenerationTimeoutException extends FlashcardGenerationException {

    public FlashcardGenerationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
