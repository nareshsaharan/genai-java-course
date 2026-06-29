package com.example.aichatbot.service;

import com.example.aichatbot.store.ImageStore;
import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageGenerateParams.Quality;
import com.openai.models.images.ImagesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * ImageService – generates images from a text prompt.
 *
 * Two modes:
 *   mock-mode = true  → returns a fake placeholder URL instantly (no API cost)
 *   mock-mode = false → calls the OpenAI image generation API
 *
 * How the clickable URL works:
 *   gpt-image-1-mini returns a base64-encoded PNG, not a hosted URL.
 *   We decode the base64 → raw PNG bytes, save them in ImageStore with a UUID,
 *   and return a local URL: http://localhost:{port}/api/multimodal/image/{uuid}
 *   The ImageController serves those bytes when the browser hits that URL.
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private static final String IMAGE_MODEL   = "gpt-image-1-mini";
    private static final int    MAX_PROMPT_CHARS = 500;
    private static final String DEFAULT_QUALITY  = "medium";

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    @Value("${server.port:8080}")
    private int serverPort;

    private final ImageStore imageStore;
    private final Optional<OpenAIClient> openAIClient;

    public ImageService(
            ImageStore imageStore,
            @Autowired(required = false) OpenAIClient openAIClient) {

        this.imageStore    = imageStore;
        this.openAIClient  = Optional.ofNullable(openAIClient);
    }

    /**
     * Generate an image and return a clickable URL to view it.
     *
     * @param prompt  text description of the image
     * @param quality low / medium / high / hd / standard / auto  (null → "medium")
     * @return a URL like http://localhost:8080/api/multimodal/image/{uuid}
     */
    public String generateImage(String prompt, String quality) {
        validatePrompt(prompt);

        String resolvedQuality = (quality == null || quality.isBlank())
                ? DEFAULT_QUALITY
                : quality.toLowerCase();

        if (mockMode) {
            log.info("[MOCK] Returning placeholder. Prompt: {}, Quality: {}", prompt, resolvedQuality);
            return generateMockUrl(prompt, resolvedQuality);
        }

        log.info("[REAL] Generating image. Prompt: {}, Quality: {}", prompt, resolvedQuality);
        return generateFromOpenAI(prompt, resolvedQuality);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty.");
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException(
                "Prompt too long: %d characters (max %d).".formatted(prompt.length(), MAX_PROMPT_CHARS)
            );
        }
    }

    private String generateMockUrl(String prompt, String quality) {
        String encoded = (prompt + " [quality=" + quality + "]").replace(" ", "+");
        return "https://placehold.co/1024x1024?text=" + encoded + "&font=roboto";
    }

    /**
     * REAL MODE steps:
     *   1. Call OpenAI → get base64-encoded PNG string.
     *   2. Decode base64 → raw PNG bytes.
     *   3. Save bytes in ImageStore under a new UUID.
     *   4. Return the local URL that ImageController will serve.
     */
    private String generateFromOpenAI(String prompt, String quality) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        ImageGenerateParams params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(IMAGE_MODEL)
            .n(1L)
            .quality(Quality.of(quality))
            .build();

        ImagesResponse response = client.images().generate(params);

        // Step 1 – extract the base64 string from the response
        String base64 = response.data()
            .filter(images -> !images.isEmpty())
            .map(images -> images.get(0))
            .flatMap(image -> image.b64Json())
            .orElseThrow(() -> new IllegalStateException(
                "OpenAI returned a response but no image data was present."));

        // Step 2 – decode base64 → raw PNG bytes
        byte[] imageBytes = Base64.getDecoder().decode(base64);

        // Step 3 – store with a unique ID
        String imageId = UUID.randomUUID().toString();
        imageStore.save(imageId, imageBytes);

        log.info("Image stored with id: {}", imageId);

        // Step 4 – return a local URL the browser can open directly
        return "http://localhost:" + serverPort + "/api/multimodal/image/" + imageId;
    }
}
