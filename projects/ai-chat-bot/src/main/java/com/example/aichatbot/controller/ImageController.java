package com.example.aichatbot.controller;

import com.example.aichatbot.dto.ImageRequest;
import com.example.aichatbot.dto.ImageResponse;
import com.example.aichatbot.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ImageController – HTTP layer for image generation.
 *
 * Exposes one endpoint:
 *
 *   POST /api/multimodal/image
 *   Body: { "prompt": "a robot teaching Java to students" }
 *
 * The controller's only job is to:
 *   1. Receive the HTTP request and parse the JSON body.
 *   2. Pass the prompt to ImageService.
 *   3. Wrap the result in an ImageResponse and return it as JSON.
 *
 * Try it:
 *   curl -X POST http://localhost:8080/api/multimodal/image \
 *        -H "Content-Type: application/json" \
 *        -d '{"prompt":"a robot teaching Java to students"}'
 */
@RestController
@RequestMapping("/api/multimodal")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * Generate an image from a text prompt.
     *
     * @param request JSON body containing the "prompt" field
     * @return JSON with "imageUrl" and "message"
     */
    @PostMapping("/image")
    public ImageResponse generateImage(@RequestBody ImageRequest request) {
        // Delegate all logic to the service — controller stays thin
        String imageUrl = imageService.generateImage(request.prompt());
        return new ImageResponse(imageUrl, "Image generated successfully");
    }

    /**
     * If the prompt is empty or too long, ImageService throws
     * IllegalArgumentException.  We catch it here and return 400 Bad Request
     * with a human-readable error message instead of a stack trace.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

    /**
     * Catch-all for unexpected errors (e.g., OpenAI API is down).
     * Returns 500 Internal Server Error with a short message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body("Something went wrong: " + ex.getMessage());
    }
}
