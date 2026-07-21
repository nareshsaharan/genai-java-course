package com.studybuddy.audio.dto;

public record AudioTranscriptionResult(
        String transcript,
        String language,
        double durationSeconds
) {
}
