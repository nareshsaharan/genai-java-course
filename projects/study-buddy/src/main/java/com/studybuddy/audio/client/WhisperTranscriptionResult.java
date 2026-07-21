package com.studybuddy.audio.client;

/** Whisper's own authoritative transcription result — {@code durationSeconds} here, unlike our pre-call estimate, is exact. */
public record WhisperTranscriptionResult(String text, String language, double durationSeconds) {
}
