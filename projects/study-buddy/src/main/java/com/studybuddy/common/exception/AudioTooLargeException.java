package com.studybuddy.common.exception;

/** Thrown when an uploaded recording exceeds the configured max file size or estimated max duration. */
public class AudioTooLargeException extends RuntimeException {

    public AudioTooLargeException(String message) {
        super(message);
    }
}
