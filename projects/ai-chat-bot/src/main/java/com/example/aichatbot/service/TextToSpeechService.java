package com.example.aichatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.speech.SpeechCreateParams.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * TextToSpeechService – converts written text into spoken audio.
 *
 * What is text-to-speech (TTS)?
 * You send in a sentence like "Your task has been added."
 * OpenAI reads it aloud using an AI voice.
 * We receive the audio bytes (an mp3 file) and send them to the browser.
 * The browser plays the audio — the user hears the sentence spoken out loud.
 *
 * Two modes:
 *   mock-mode = true  → returns a tiny valid silent mp3 (no API call)
 *   mock-mode = false → calls the real OpenAI TTS API (gpt-4o-mini-tts)
 *
 * Available voices: alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse
 * We default to "alloy" — a clear, neutral voice great for demos.
 */
@Service
public class TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechService.class);

    private static final String TTS_MODEL    = "gpt-4o-mini-tts";
    private static final Voice  DEFAULT_VOICE = Voice.ALLOY;
    private static final int    MAX_TEXT_CHARS = 500;

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    private final Optional<OpenAIClient> openAIClient;

    public TextToSpeechService(@Autowired(required = false) OpenAIClient openAIClient) {
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    /**
     * Convert text to speech and return the raw mp3 bytes.
     *
     * @param text the sentence or phrase to speak aloud
     * @return byte array containing a valid mp3 audio file
     */
    public byte[] speak(String text) throws IOException {

        // Step 1 – validate before touching the API
        validateText(text);

        // Step 2 – route to mock or real implementation
        if (mockMode) {
            log.info("[MOCK] TTS skipped. Returning silent mp3 placeholder.");
            return silentMp3();
        }

        log.info("[REAL] Converting text to speech: \"{}\"", text);
        return speakWithOpenAI(text);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be empty.");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException(
                "Text too long: %d characters (max %d).".formatted(text.length(), MAX_TEXT_CHARS)
            );
        }
    }

    /**
     * REAL MODE:
     *   1. Build a SpeechCreateParams with the text, model, and voice.
     *   2. Call client.audio().speech().create() — OpenAI reads the text aloud.
     *   3. The response is an HttpResponse whose body() is an InputStream of mp3 bytes.
     *   4. Read all those bytes into a byte array and return it.
     *
     * Why return bytes instead of a stream?
     * We need to know the Content-Length for the HTTP response header.
     * Reading everything into a byte array first makes that easy.
     */
    private byte[] speakWithOpenAI(String text) throws IOException {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        SpeechCreateParams params = SpeechCreateParams.builder()
            .input(text)           // the text to speak
            .model(TTS_MODEL)      // gpt-4o-mini-tts
            .voice(DEFAULT_VOICE)  // alloy — clear, neutral voice
            .build();

        // create() returns an HttpResponse — the body is the raw mp3 audio stream
        try (HttpResponse response = client.audio().speech().create(params)) {
            return response.body().readAllBytes();
        }
    }

    /**
     * MOCK MODE: returns the smallest valid mp3 file possible (44 bytes).
     * This is a real mp3 header with no audio content — browsers accept it
     * without errors but play nothing (silent).
     *
     * Why a real mp3 and not just empty bytes?
     * Empty bytes would cause the browser to throw a media decode error.
     * A valid (but silent) mp3 lets the whole pipeline work end-to-end
     * without needing a real API key.
     */
    private byte[] silentMp3() {
        // Minimal valid ID3v2 + MPEG frame header (silent audio)
        return new byte[] {
            (byte)0xFF, (byte)0xFB, (byte)0x90, 0x00,  // MPEG frame sync + header
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00
        };
    }
}
