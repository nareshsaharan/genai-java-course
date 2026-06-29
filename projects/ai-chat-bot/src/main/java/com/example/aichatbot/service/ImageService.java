package com.example.aichatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import com.openai.models.images.ImageGenerateParams.Quality;
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

    // Default quality used when the caller doesn't specify one.
    private static final String DEFAULT_QUALITY = "medium";

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    // OpenAIClient is Optional because it only exists when mock-mode=false.
    // Spring injects null when the bean is missing; we wrap it in Optional.
    private final Optional<OpenAIClient> openAIClient;

    public ImageService(@Autowired(required = false) OpenAIClient openAIClient) {
        this.openAIClient = Optional.ofNullable(openAIClient);
    }

    /**
     * Generate an image from the given prompt and quality setting.
     *
     * @param prompt  text description of the image to generate
     * @param quality desired quality level (low / medium / high / hd / standard / auto)
     *                pass null or blank to use the default ("medium")
     * @return a URL string pointing to the generated image
     */
    public String generateImage(String prompt, String quality) {

        // Step 1 – validate the prompt before doing anything else
        validatePrompt(prompt);

        // Step 2 – fall back to default quality if none was provided
        String resolvedQuality = (quality == null || quality.isBlank()) ? DEFAULT_QUALITY : quality.toLowerCase();

        // Step 3 – route to mock or real implementation
        if (mockMode) {
            log.info("[MOCK] Image generation skipped. Prompt: {}, Quality: {}", prompt, resolvedQuality);
            return generateMockUrl(prompt, resolvedQuality);
        }

        log.info("[REAL] Generating image. Prompt: {}, Quality: {}", prompt, resolvedQuality);
        return generateFromOpenAI(prompt, resolvedQuality);
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
    private String generateMockUrl(String prompt, String quality) {
        // URL-encode spaces as + for a valid URL
        String encoded = (prompt + " [quality=" + quality + "]").replace(" ", "+");
        return "https://placehold.co/1024x1024?text=" + encoded + "&font=roboto";
    }

    /**
     * REAL MODE: calls the OpenAI Images API to generate one image.
     *
     * Why base64 instead of a URL?
     * gpt-image-1 and gpt-image-1-mini always return the image as base64-encoded
     * data — they do NOT return a hosted URL like DALL-E 3 does.
     * We wrap the base64 string in a "data URI" so any browser or HTTP client
     * can display it directly:
     *
     *   data:image/png;base64,<base64-string>
     *
     * Quality values (low → cheapest/fastest, high → best detail):
     *   low, medium, high, standard, hd, auto
     */
    private String generateFromOpenAI(String prompt, String quality) {
        OpenAIClient client = openAIClient.orElseThrow(() ->
            new IllegalStateException(
                "OpenAI client not initialised. Check app.mock-mode and OPENAI_API_KEY.")
        );

        // Convert the quality string to the SDK's Quality enum.
        Quality sdkQuality = Quality.of(quality);

        // Build the request parameters
        ImageGenerateParams params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(IMAGE_MODEL)
            .n(1L)              // generate exactly one image
            .quality(sdkQuality)
            .build();

        // Call the API — returns an ImagesResponse containing a list of Image objects
        ImagesResponse response = client.images().generate(params);

        // gpt-image-1-mini returns base64 data, not a hosted URL.
        // We convert it to a data URI so it can be used directly in a browser <img> tag.
        String base64 = response.data()
            .filter(images -> !images.isEmpty())
            .map(images -> images.get(0))
            .flatMap(image -> image.b64Json())
            .orElseThrow(() -> new IllegalStateException(
                "OpenAI returned a response but no image data was present."));

        return "data:image/png;base64," + base64;
    }
}
