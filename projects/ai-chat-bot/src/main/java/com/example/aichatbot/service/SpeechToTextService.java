package com.example.aichatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * SpeechToTextService – converts audio bytes into written text.
 *
 * What is speech-to-text (STT)?
 * The user records their voice (or provides an audio file).
 * We send that audio to OpenAI's Whisper model.
 * OpenAI listens to the audio and writes down what was said.
 * We receive that written text and return it to the caller.
 *
 * Two modes:
 *   mock-mode = true  → returns fake text instantly (no API call, great for demos)
 *   mock-mode = false → sends the real audio bytes to OpenAI Whisper
 *
 * Why does this service accept filename + byte[] instead of a file object?
 * This app uses Spring WebFlux (reactive).  In WebFlux, uploaded files come
 * in as FilePart objects — not the MultipartFile you'd use in a normal Spring MVC app.
 * The controller handles the reactive file-reading; this service just gets the
 * plain filename and bytes, keeping it simple and easy to understand.
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    private static final String WHISPER_MODEL = "whisper-1";
    private static final long   MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a");

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    private final Optional<OpenAIClient> openAIClient;

    public SpeechToTextService(@Autowired(required = false) OpenAIClient openAIClient) {
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    /**
     * Transcribe audio bytes to text.
     *
     * @param filename  original file name (e.g. "audio.mp3") — used for extension check
     * @param audioBytes raw audio file contents
     * @return the transcribed text
     */
    public String transcribe(String filename, byte[] audioBytes) {

        // Step 1 – validate before touching the API
        validateFile(filename, audioBytes);

        // Step 2 – route to mock or real implementation
        if (mockMode) {
            log.info("[MOCK] Transcription skipped. File: {}", filename);
            return "Add Java class tomorrow";
        }

        log.info("[REAL] Transcribing: {} ({} bytes)", filename, audioBytes.length);
        return transcribeWithOpenAI(filename, audioBytes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void validateFile(String filename, byte[] audioBytes) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("File must have a name (e.g. audio.mp3).");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio file must not be empty.");
        }
        if (audioBytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                "File too large: %.1f MB (max 10 MB).".formatted(audioBytes.length / (1024.0 * 1024.0))
            );
        }

        String extension = filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
            : "";

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                "Unsupported file type: ." + extension + ". Allowed: mp3, wav, m4a."
            );
        }
    }

    /**
     * REAL MODE:
     *   1. Wrap the audio bytes in a MultipartField so the SDK sends the filename
     *      to OpenAI — without it, OpenAI can't detect the audio format and rejects
     *      the request with a 400 error.
     *   2. Call client.audio().transcriptions().create().
     *   3. Extract the text string from the response.
     */
    private String transcribeWithOpenAI(String filename, byte[] audioBytes) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // The SDK's .file() accepts MultipartField<InputStream>, not byte[].
        // We wrap our byte[] in a ByteArrayInputStream to satisfy the type,
        // and attach the filename so OpenAI knows the audio format (.mp3, .wav, etc.).
        InputStream audioStream = new ByteArrayInputStream(audioBytes);
        MultipartField<InputStream> fileField = MultipartField.<InputStream>builder()
            .value(audioStream)
            .filename(filename)
            .contentType("audio/mpeg")
            .build();

        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
            .file(fileField)
            .model(WHISPER_MODEL)
            .build();

        TranscriptionCreateResponse response = client.audio().transcriptions().create(params);

        return response.asTranscription().text();
    }
}
