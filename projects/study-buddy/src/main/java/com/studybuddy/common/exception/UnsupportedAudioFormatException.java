package com.studybuddy.common.exception;

/** Thrown when an uploaded audio file's extension/content-type isn't one Whisper accepts. */
public class UnsupportedAudioFormatException extends RuntimeException {

    public UnsupportedAudioFormatException(String message) {
        super(message);
    }
}
