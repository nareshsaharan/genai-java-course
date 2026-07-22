package com.studybuddy.common.exception;

/** Thrown when an embedding is attempted but no OpenAI API key is configured (env var or Settings). */
public class EmbeddingsNotConfiguredException extends RuntimeException {

    public EmbeddingsNotConfiguredException(String message) {
        super(message);
    }
}
