package com.studybuddy.common.exception;

/** Thrown when the Claude model call times out while generating a tutor answer. */
public class TutorAnswerTimeoutException extends TutorAnswerGenerationException {

    public TutorAnswerTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
