package com.example.lmsmcp.exception;

/**
 * Thrown by the service layer when input is invalid (blank courseId,
 * studentId, topic, or difficulty) or the requested resource cannot be
 * found. The MCP tool layer catches this and turns it into a readable
 * ErrorResponse instead of letting a raw exception reach the client.
 */
public class LmsException extends RuntimeException {

    public LmsException(String message) {
        super(message);
    }
}
