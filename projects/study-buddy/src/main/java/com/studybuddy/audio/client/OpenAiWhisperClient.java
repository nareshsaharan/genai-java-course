package com.studybuddy.audio.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.settings.RuntimeSecretsService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Direct HTTP call to OpenAI's {@code /v1/audio/transcriptions} endpoint
 * with {@code response_format=verbose_json}. Not built on LangChain4j's
 * {@code OpenAiAudioTranscriptionModel}: that abstraction's
 * {@code AudioTranscriptionResponse} only exposes {@code text()} — no
 * language or duration — which this project's response contract requires.
 * {@code verbose_json} is only supported by the {@code whisper-1} model
 * (the newer gpt-4o-transcribe family only supports streaming json/text).
 *
 * <p>When no key is configured for the session (Mock Mode — the default for
 * every new session), no real API call is made: a canned transcript is
 * returned instead, so voice input stays usable with zero API keys.
 */
@Component
public class OpenAiWhisperClient implements WhisperClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWhisperClient.class);
    private static final String TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(400);

    private final AudioProperties properties;
    private final RuntimeSecretsService secrets;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiWhisperClient(AudioProperties properties, ObjectMapper objectMapper, RuntimeSecretsService secrets) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.secrets = secrets;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
    }

    @Override
    public WhisperTranscriptionResult transcribe(byte[] audioBytes, String filename, String mimeType) {
        if (!StringUtils.hasText(secrets.getOpenAiKey())) {
            return mockTranscription();
        }

        int maxAttempts = properties.maxRetries() + 1;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return doTranscribe(audioBytes, filename, mimeType);
            } catch (HttpTimeoutException e) {
                lastFailure = new WhisperClientTimeoutException(
                        "Whisper call timed out (attempt " + attempt + "/" + maxAttempts + ")", e);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                lastFailure = new WhisperClientException(
                        "Whisper call failed (attempt " + attempt + "/" + maxAttempts + ")", e);
            } catch (RetryableHttpStatusException e) {
                lastFailure = new WhisperClientException(
                        "Whisper API returned HTTP " + e.statusCode + " (attempt " + attempt + "/" + maxAttempts + ")", e);
            }

            if (attempt < maxAttempts) {
                sleepBeforeRetry(attempt);
            }
        }

        log.warn("Whisper transcription failed after {} attempt(s)", maxAttempts);
        throw lastFailure;
    }

    private static WhisperTranscriptionResult mockTranscription() {
        return new WhisperTranscriptionResult(
                "This is a mock transcription — Mock Mode is active because no OpenAI API key is "
                        + "configured for this session. Add your OpenAI API key in the Settings tab "
                        + "for real speech-to-text.",
                "en", 0.0);
    }

    private WhisperTranscriptionResult doTranscribe(byte[] audioBytes, String filename, String mimeType)
            throws IOException, InterruptedException {
        String boundary = "study-buddy-" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, audioBytes, filename, mimeType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TRANSCRIPTION_URL))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + secrets.getOpenAiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw new RetryableHttpStatusException(response.statusCode());
        }
        if (response.statusCode() != 200) {
            throw new WhisperClientException(
                    "Whisper API returned HTTP " + response.statusCode() + ": " + truncate(response.body()), null);
        }

        VerboseTranscriptionResponse parsed = objectMapper.readValue(response.body(), VerboseTranscriptionResponse.class);
        return new WhisperTranscriptionResult(parsed.text(), parsed.language(), parsed.duration());
    }

    private byte[] buildMultipartBody(String boundary, byte[] audioBytes, String filename, String mimeType) throws IOException {
        List<byte[]> parts = new ArrayList<>();
        String lineBreak = "\r\n";

        parts.add(textField(boundary, "model", properties.model(), lineBreak));
        parts.add(textField(boundary, "response_format", "verbose_json", lineBreak));

        StringBuilder fileHeader = new StringBuilder()
                .append("--").append(boundary).append(lineBreak)
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(filename).append('"').append(lineBreak)
                .append("Content-Type: ").append(mimeType).append(lineBreak).append(lineBreak);
        parts.add(fileHeader.toString().getBytes(StandardCharsets.UTF_8));
        parts.add(audioBytes);
        parts.add(lineBreak.getBytes(StandardCharsets.UTF_8));

        parts.add(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));

        int totalLength = parts.stream().mapToInt(p -> p.length).sum();
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, pos, part.length);
            pos += part.length;
        }
        return result;
    }

    private static byte[] textField(String boundary, String name, String value, String lineBreak) {
        String field = "--" + boundary + lineBreak
                + "Content-Disposition: form-data; name=\"" + name + "\"" + lineBreak + lineBreak
                + value + lineBreak;
        return field.getBytes(StandardCharsets.UTF_8);
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis() * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VerboseTranscriptionResponse(String text, String language, double duration) {
    }

    /** Internal signal that this HTTP status should trigger a retry, not an immediate failure. */
    private static final class RetryableHttpStatusException extends RuntimeException {
        private final int statusCode;

        private RetryableHttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }
    }
}
