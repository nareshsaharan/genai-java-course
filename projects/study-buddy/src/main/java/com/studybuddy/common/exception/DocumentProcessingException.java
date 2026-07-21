package com.studybuddy.common.exception;

/** Thrown when text extraction, chunking or embedding of an uploaded document fails. */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentProcessingException(String message) {
        super(message);
    }
}
