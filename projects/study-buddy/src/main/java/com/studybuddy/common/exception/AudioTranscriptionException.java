package com.studybuddy.common.exception;

/** Thrown when the Whisper API call fails (non-timeout error). */
public class AudioTranscriptionException extends RuntimeException {

    public AudioTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
