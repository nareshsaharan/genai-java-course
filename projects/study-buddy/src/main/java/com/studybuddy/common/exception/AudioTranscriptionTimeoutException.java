package com.studybuddy.common.exception;

/** Thrown when the Whisper API call times out even after retries. */
public class AudioTranscriptionTimeoutException extends AudioTranscriptionException {

    public AudioTranscriptionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
