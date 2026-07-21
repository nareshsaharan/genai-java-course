package com.studybuddy.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for optional voice input (Whisper transcription via OpenAI).
 * {@code apiKey} is deliberately NOT {@code @NotBlank} — unlike Claude, this
 * whole feature is optional per the project requirements, so the app must
 * still start with no key configured. {@code AudioTranscriptionService}
 * checks for a blank key at call time and fails that one request with a
 * clear 503, rather than failing application startup.
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.audio")
public record AudioProperties(

        String apiKey,

        @NotBlank
        String model,

        @Positive
        long maxFileSizeBytes,

        @Positive
        int maxDurationSeconds,

        @Positive
        int timeoutSeconds,

        @Positive
        int maxRetries,

        boolean persistRecordings,

        @NotBlank
        String recordingsDirectory,

        /** Blank means "use the JVM's default temp directory". Configurable mainly so tests can point it at a throwaway dir. */
        String tempDirectory
) {
}
