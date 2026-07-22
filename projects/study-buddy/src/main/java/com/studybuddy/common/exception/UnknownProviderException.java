package com.studybuddy.common.exception;

/** Thrown when a Settings request names a chat/embedding provider that doesn't exist. */
public class UnknownProviderException extends RuntimeException {

    public UnknownProviderException(String message) {
        super(message);
    }
}
