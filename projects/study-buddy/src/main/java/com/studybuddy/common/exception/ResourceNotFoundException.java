package com.studybuddy.common.exception;

/** Thrown when a requested document, chunk, quiz or topic does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
