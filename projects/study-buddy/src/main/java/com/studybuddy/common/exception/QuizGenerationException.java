package com.studybuddy.common.exception;

/** Thrown when the Claude model fails (non-timeout error) or returns unparseable output while generating a quiz. */
public class QuizGenerationException extends RuntimeException {

    public QuizGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
