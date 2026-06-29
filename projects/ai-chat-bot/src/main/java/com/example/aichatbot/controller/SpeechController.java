package com.example.aichatbot.controller;

import com.example.aichatbot.dto.TranscriptionResponse;
import com.example.aichatbot.service.SpeechToTextService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * SpeechController – HTTP layer for speech-to-text transcription.
 *
 * Exposes one endpoint:
 *
 *   POST /api/multimodal/transcribe
 *   Content-Type: multipart/form-data
 *   Field name:   file
 *   Accepted:     mp3, wav, m4a (max 10 MB)
 *
 * What is multipart/form-data?
 * It's a way to upload files over HTTP.  Instead of sending JSON text,
 * the request contains the actual file bytes (like attaching a file to an email).
 * Spring's @RequestParam MultipartFile automatically reads the uploaded file for us.
 *
 * Try it:
 *   curl -X POST http://localhost:8080/api/multimodal/transcribe \
 *        -F "file=@/path/to/audio.mp3"
 */
@RestController
@RequestMapping("/api/multimodal")
public class SpeechController {

    private final SpeechToTextService speechToTextService;

    public SpeechController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    /**
     * Transcribe an uploaded audio file to text.
     *
     * @RequestParam("file") MultipartFile
     *   Spring automatically reads the "file" field from the multipart request
     *   and gives us a MultipartFile object — no manual byte-reading needed.
     *
     * @return JSON with a "text" field containing the transcribed words
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@RequestParam("file") MultipartFile file) throws IOException {
        String transcribedText = speechToTextService.transcribe(file);
        return new TranscriptionResponse(transcribedText);
    }

    /**
     * Bad file input: empty file, wrong format, too large.
     * Returns 400 Bad Request with a readable error message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

    /**
     * Catch-all: IO errors (disk full, stream closed) or OpenAI errors.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body("Something went wrong: " + ex.getMessage());
    }
}
