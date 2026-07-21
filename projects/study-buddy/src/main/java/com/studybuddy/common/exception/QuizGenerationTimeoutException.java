package com.studybuddy.common.exception;

/** Thrown when the Claude model call times out while generating a quiz. */
public class QuizGenerationTimeoutException extends QuizGenerationException {

    public QuizGenerationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
