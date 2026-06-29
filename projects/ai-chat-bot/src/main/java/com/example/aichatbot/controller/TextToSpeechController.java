package com.example.aichatbot.controller;

import com.example.aichatbot.dto.SpeakRequest;
import com.example.aichatbot.service.TextToSpeechService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * TextToSpeechController – HTTP layer for text-to-speech conversion.
 *
 * Endpoint:
 *   POST /api/multimodal/speak
 *   Body: { "text": "Your task has been added" }
 *
 * What does the response look like?
 * Unlike most APIs that return JSON, this one returns an audio file directly.
 * The Content-Type is "audio/mpeg" (mp3).  This means:
 *   - curl saves it as a file you can play
 *   - A browser can play it with the HTML <audio> tag
 *   - A frontend app can pipe it to a media player
 *
 * Try it — save and play from terminal:
 *   curl -s -X POST http://localhost:8080/api/multimodal/speak \
 *        -H "Content-Type: application/json" \
 *        -d '{"text":"Your task has been added"}' \
 *        -o output.mp3 && afplay output.mp3
 *
 * Why Mono<ResponseEntity<byte[]>>?
 * This app uses Spring WebFlux (reactive).  Mono means "one value, delivered asynchronously."
 * We run the blocking OpenAI call on a background thread (boundedElastic)
 * so it doesn't stall the reactive event loop.
 */
@RestController
@RequestMapping("/api/multimodal")
public class TextToSpeechController {

    private final TextToSpeechService textToSpeechService;

    public TextToSpeechController(TextToSpeechService textToSpeechService) {
        this.textToSpeechService = textToSpeechService;
    }

    /**
     * Convert text to speech and return the mp3 audio bytes.
     *
     * @param request JSON body with a "text" field
     * @return mp3 audio file as binary response
     */
    @PostMapping("/speak")
    public Mono<ResponseEntity<byte[]>> speak(@RequestBody SpeakRequest request) {

        // Run on a background thread because the OpenAI call is blocking
        return Mono.fromCallable(() -> textToSpeechService.speak(request.text()))
            .subscribeOn(Schedulers.boundedElastic())
            .map(audioBytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"speech.mp3\"")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .contentLength(audioBytes.length)
                .body(audioBytes)
            );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body("Something went wrong: " + ex.getMessage());
    }
}
