package com.example.aichatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * SpeechToTextService – converts an audio file into written text.
 *
 * What is speech-to-text (STT)?
 * The user records their voice (or provides an audio file).
 * We send that audio to OpenAI's Whisper model.
 * OpenAI listens to the audio and writes down what was said.
 * We receive that written text and return it to the caller.
 *
 * Two modes:
 *   mock-mode = true  → returns fake text instantly (no API call, great for demos)
 *   mock-mode = false → sends the real audio file to OpenAI Whisper
 *
 * Supported formats: mp3, wav, m4a (the most common audio formats).
 * Max file size: 10 MB — large files are expensive and slow.
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    // Whisper is OpenAI's speech-to-text model.
    private static final String WHISPER_MODEL = "whisper-1";

    // Maximum file size we accept: 10 MB in bytes.
    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

    // Only allow these audio formats.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a");

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    // OpenAIClient is Optional because it only exists when mock-mode=false.
    private final Optional<OpenAIClient> openAIClient;

    public SpeechToTextService(@Autowired(required = false) OpenAIClient openAIClient) {
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    /**
     * Transcribe the given audio file to text.
     *
     * @param file the audio file uploaded by the user (mp3, wav, or m4a)
     * @return the transcribed text
     */
    public String transcribe(MultipartFile file) throws IOException {

        // Step 1 – validate the file before touching the API
        validateFile(file);

        // Step 2 – route to mock or real implementation
        if (mockMode) {
            log.info("[MOCK] Transcription skipped. File: {}", file.getOriginalFilename());
            return "Add Java class tomorrow";
        }

        log.info("[REAL] Transcribing file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
        return transcribeWithOpenAI(file);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates that the file is usable before we send it to OpenAI.
     * Throws IllegalArgumentException for bad input — the controller
     * turns this into a 400 Bad Request response automatically.
     */
    private void validateFile(MultipartFile file) {
        // Check the file was actually attached
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file must not be empty.");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                "File too large: %.1f MB (max 10 MB). Please use a shorter recording."
                    .formatted(file.getSize() / (1024.0 * 1024.0))
            );
        }

        // Check file extension — only allow known audio formats
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("File must have a name (e.g. audio.mp3).");
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
     *
     * How this works:
     *   1. Read the audio bytes from the uploaded file.
     *   2. Build a TranscriptionCreateParams with the bytes + model name.
     *   3. Call client.audio().transcriptions().create() — OpenAI listens and returns text.
     *   4. Extract the transcribed text from the response.
     *
     * Why pass bytes instead of a file path?
     * The file is uploaded to the server's memory (MultipartFile), not saved to disk.
     * The OpenAI SDK accepts raw bytes directly, so we never need to write the file
     * to disk ourselves.
     */
    private String transcribeWithOpenAI(MultipartFile file) throws IOException {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // Step 1 – read the audio file bytes from the multipart upload
        byte[] audioBytes = file.getBytes();

        // Step 2 – build the transcription request
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
            .file(audioBytes)              // the raw audio bytes
            .model(WHISPER_MODEL)          // whisper-1 is OpenAI's STT model
            .build();

        // Step 3 – send to OpenAI and wait for the text response
        // This is a blocking call — OpenAI processes the audio and returns text.
        TranscriptionCreateResponse response = client.audio().transcriptions().create(params);

        // Step 4 – extract the text
        // The response can be a Transcription or a TranscriptionVerbose.
        // asTranscription() gives us the simple version with just the text.
        return response.asTranscription().text();
    }
}
