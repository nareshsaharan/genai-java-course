package com.studybuddy.settings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.studybuddy.common.exception.ApiKeyValidationException;

/**
 * Confirms a submitted OpenAI API key actually works before
 * {@link RuntimeSecretsService} persists it. Calls the cheap
 * {@code GET /v1/models} endpoint with the <em>submitted</em> key — enough
 * to prove authentication works without consuming any Whisper transcription
 * minutes.
 */
@Component
public class OpenAiKeyValidator {

    private static final String MODELS_URL = "https://api.openai.com/v1/models";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Function<String, HttpResponse<String>> prober;

    @Autowired
    public OpenAiKeyValidator() {
        this(OpenAiKeyValidator::probeRealEndpoint);
    }

    /** Package-visible seam for tests — injects a fake prober instead of a real network call. */
    OpenAiKeyValidator(Function<String, HttpResponse<String>> prober) {
        this.prober = prober;
    }

    public void validate(String apiKey) {
        HttpResponse<String> response = prober.apply(apiKey);
        if (response.statusCode() != 200) {
            throw new ApiKeyValidationException(
                    "OpenAI rejected this key (HTTP " + response.statusCode() + "): " + truncate(response.body()));
        }
    }

    private static HttpResponse<String> probeRealEndpoint(String apiKey) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_URL))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ApiKeyValidationException("Could not reach OpenAI to verify this key: " + e.getMessage());
        }
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
