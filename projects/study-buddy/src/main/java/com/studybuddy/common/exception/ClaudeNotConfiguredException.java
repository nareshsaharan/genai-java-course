package com.studybuddy.common.exception;

/** Thrown when a Claude call is attempted but no Anthropic API key is configured (env var or Settings). */
public class ClaudeNotConfiguredException extends RuntimeException {

    public ClaudeNotConfiguredException(String message) {
        super(message);
    }
}
