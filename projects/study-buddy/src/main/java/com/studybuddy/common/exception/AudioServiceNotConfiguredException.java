package com.studybuddy.common.exception;

/** Thrown when voice input is used but OPENAI_API_KEY hasn't been configured — this feature is optional. */
public class AudioServiceNotConfiguredException extends RuntimeException {

    public AudioServiceNotConfiguredException(String message) {
        super(message);
    }
}
