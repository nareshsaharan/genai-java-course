package com.studybuddy.common.exception;

/** Thrown when an uploaded file's extension/content-type is not PDF, TXT or Markdown. */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
