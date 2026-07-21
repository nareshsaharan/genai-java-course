package com.studybuddy.common.exception;

/** Thrown when a submitted API key fails verification against the real provider before being saved. */
public class ApiKeyValidationException extends RuntimeException {

    public ApiKeyValidationException(String message) {
        super(message);
    }
}
