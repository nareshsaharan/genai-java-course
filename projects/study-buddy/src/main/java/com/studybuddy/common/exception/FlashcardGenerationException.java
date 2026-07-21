package com.studybuddy.common.exception;

/** Thrown when the Claude model fails (non-timeout error) or returns unparseable output while generating flashcards. */
public class FlashcardGenerationException extends RuntimeException {

    public FlashcardGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
