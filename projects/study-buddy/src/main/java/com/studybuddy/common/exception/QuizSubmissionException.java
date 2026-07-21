package com.studybuddy.common.exception;

/** Thrown when a quiz submission doesn't match the quiz it's submitted against (wrong/missing/extra question ids). */
public class QuizSubmissionException extends RuntimeException {

    public QuizSubmissionException(String message) {
        super(message);
    }
}
