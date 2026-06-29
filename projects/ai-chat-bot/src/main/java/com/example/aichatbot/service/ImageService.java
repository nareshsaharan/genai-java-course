package com.example.aichatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ImageService – generates images from a text prompt.
 *
 * What does this service do?
 * It takes a text description (called a "prompt") like
 * "a robot teaching Java to students" and returns a URL pointing to
 * a generated image.
 *
 * Two modes:
 *   mock-mode = true  → returns a fake placeholder URL instantly (no API cost)
 *   mock-mode = false → calls the real OpenAI image generation API
 *
 * Why validate the prompt here and not just in the controller?
 * The service is the right place for business rules. Controllers should only
 * handle HTTP concerns (parsing requests, returning responses).
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    // The model we use for image generation.
    // gpt-image-1 is OpenAI's image generation model.
    private static final String IMAGE_MODEL = "gpt-image-1-mini";

    // Maximum characters allowed in a prompt (keeps costs predictable).
    private static final int MAX_PROMPT_CHARS = 500;

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    // OpenAIClient is Optional because it only exists when mock-mode=false.
    // Spring injects null when the bean is missing; we wrap it in Optional.
    private final Optional<OpenAIClient> openAIClient;

    public ImageService(@Autowired(required = false) OpenAIClient openAIClient) {
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    /**
     * Generate an image from the given prompt.
     *
     * @param prompt text description of the image to generate
     * @return a URL string pointing to the generated image
     */
    public String generateImage(String prompt) {

        // Step 1 – validate the prompt before doing anything else
        validatePrompt(prompt);

        // Step 2 – route to mock or real implementation
        if (mockMode) {
            log.info("[MOCK] Image generation skipped. Prompt: {}", prompt);
            return generateMockUrl(prompt);
        }

        log.info("[REAL] Generating image for prompt: {}", prompt);
        return generateFromOpenAI(prompt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates the prompt.
     * Throws IllegalArgumentException if the prompt is blank or too long.
     * The controller's @ExceptionHandler will convert this into a 400 response.
     */
    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty.");
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException(
                "Prompt too long: %d characters (max %d)."
                    .formatted(prompt.length(), MAX_PROMPT_CHARS)
            );
        }
    }

    /**
     * MOCK MODE: returns a placeholder image URL so students can test the
     * endpoint without an OpenAI API key or any network calls.
     *
     * We embed the prompt in the URL so the response feels meaningful.
     */
    private String generateMockUrl(String prompt) {
        // URL-encode spaces as + for a valid URL
        String encoded = prompt.replace(" ", "+");
        return "https://placehold.co/1024x1024?text=" + encoded + "&font=roboto";
    }

    /**
     * REAL MODE: calls the OpenAI Images API to generate one image.
     *
     * Key steps:
     *   1. Build ImageGenerateParams with the prompt, model, and n=1.
     *   2. Call client.images().generate() — this is a blocking (non-streaming) call.
     *   3. Extract the URL from the first image in the response.
     *
     * Why n(1)?
     * n controls how many images to generate. We always request exactly 1
     * to keep costs low and the response simple.
     */
    private String generateFromOpenAI(String prompt) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // Build the request parameters
        ImageGenerateParams params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(IMAGE_MODEL)
            .n(1L)         // generate exactly one image
            .build();

        // Call the API — returns an ImagesResponse containing a list of Image objects
        ImagesResponse response = client.images().generate(params);

        // Extract the URL from the first (and only) image in the list
        return response.data()
            .filter(images -> !images.isEmpty())
            .map(images -> images.get(0))
            .flatMap(image -> image.url())
            .orElseThrow(() -> new IllegalStateException(
                "OpenAI returned a response but no image URL was present."));
    }
}
