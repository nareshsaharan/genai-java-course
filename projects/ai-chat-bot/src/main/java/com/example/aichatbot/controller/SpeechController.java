package com.example.aichatbot.controller;

import com.example.aichatbot.dto.TranscriptionResponse;
import com.example.aichatbot.service.SpeechToTextService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * SpeechController – HTTP layer for speech-to-text transcription.
 *
 * Endpoint:
 *   POST /api/multimodal/transcribe
 *   Content-Type: multipart/form-data
 *   Field:        file  (mp3, wav, m4a — max 10 MB)
 *
 * Why FilePart instead of MultipartFile?
 * This app uses Spring WebFlux (the reactive web framework).  In WebFlux,
 * uploaded files are represented as FilePart, not MultipartFile.
 * MultipartFile belongs to Spring MVC (the classic, non-reactive version).
 * They do the same thing — give us the uploaded file — just in different styles.
 *
 * How the reactive pipeline works:
 *   1. filePart.content()          → Flux<DataBuffer>  (stream of byte chunks)
 *   2. DataBufferUtils.join(...)   → Mono<DataBuffer>  (all chunks merged into one)
 *   3. .map(...)                   → read bytes, call service, build response
 *   4. .subscribeOn(boundedElastic)→ run the blocking OpenAI call on a thread pool
 *
 * Try it:
 *   curl -X POST http://localhost:8080/api/multimodal/transcribe -F "file=@audio.mp3"
 */
@RestController
@RequestMapping("/api/multimodal")
public class SpeechController {

    private final SpeechToTextService speechToTextService;

    public SpeechController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<TranscriptionResponse> transcribe(@RequestPart("file") FilePart filePart) {

        // Step 1 – join all incoming byte chunks into a single DataBuffer
        return DataBufferUtils.join(filePart.content())
            .map(dataBuffer -> {
                // Step 2 – copy DataBuffer bytes into a plain byte array
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer); // release memory back to the pool
                return bytes;
            })
            // Step 3 – call the service (blocking) on a thread pool so we don't stall WebFlux
            .publishOn(Schedulers.boundedElastic())
            .map(bytes -> {
                String text = speechToTextService.transcribe(filePart.filename(), bytes);
                return new TranscriptionResponse(text);
            });
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
