package com.studybuddy.common.exception;

/** Thrown when no ingested course-note chunk is relevant enough to ground a generation request. */
public class NoRelevantContextException extends RuntimeException {

    public NoRelevantContextException(String message) {
        super(message);
    }
}
