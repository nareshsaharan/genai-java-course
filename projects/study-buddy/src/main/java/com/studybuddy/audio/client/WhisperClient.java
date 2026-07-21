package com.studybuddy.audio.client;

/**
 * Abstraction over the Whisper speech-to-text call, so
 * {@code AudioTranscriptionService} can be unit-tested against a mock
 * without ever making a real network call or spending API credits.
 */
public interface WhisperClient {

    /**
     * @throws WhisperClientTimeoutException if every attempt (including retries) times out
     * @throws WhisperClientException        if every attempt fails for any other reason
     */
    WhisperTranscriptionResult transcribe(byte[] audioBytes, String filename, String mimeType);
}
