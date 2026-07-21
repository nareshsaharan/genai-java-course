package com.studybuddy.common.exception;

/** Thrown when the Claude model fails (non-timeout error) while generating a tutor answer. */
public class TutorAnswerGenerationException extends RuntimeException {

    public TutorAnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
