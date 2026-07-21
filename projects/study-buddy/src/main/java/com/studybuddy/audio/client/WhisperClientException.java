package com.studybuddy.audio.client;

/** Thrown by {@link WhisperClient} after retries are exhausted (non-timeout failure). */
public class WhisperClientException extends RuntimeException {

    public WhisperClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
