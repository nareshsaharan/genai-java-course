package com.studybuddy.audio.client;

/** Thrown by {@link WhisperClient} when every attempt (including retries) times out. */
public class WhisperClientTimeoutException extends WhisperClientException {

    public WhisperClientTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
