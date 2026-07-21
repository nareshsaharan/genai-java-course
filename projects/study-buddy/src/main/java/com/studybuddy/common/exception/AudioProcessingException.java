package com.studybuddy.common.exception;

/** Thrown for a structurally invalid upload (empty file, unreadable multipart part) — not a Whisper API failure. */
public class AudioProcessingException extends RuntimeException {

    public AudioProcessingException(String message) {
        super(message);
    }

    public AudioProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
