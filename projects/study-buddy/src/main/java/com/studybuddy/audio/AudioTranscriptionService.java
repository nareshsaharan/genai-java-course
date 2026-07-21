package com.studybuddy.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.studybuddy.audio.client.WhisperClient;
import com.studybuddy.audio.client.WhisperClientException;
import com.studybuddy.audio.client.WhisperClientTimeoutException;
import com.studybuddy.audio.client.WhisperTranscriptionResult;
import com.studybuddy.audio.dto.AudioTranscriptionResult;
import com.studybuddy.common.exception.AudioProcessingException;
import com.studybuddy.common.exception.AudioTooLargeException;
import com.studybuddy.common.exception.AudioTranscriptionException;
import com.studybuddy.common.exception.AudioTranscriptionTimeoutException;
import com.studybuddy.common.exception.UnsupportedAudioFormatException;
import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.observability.StudyBuddyMetrics;

/**
 * Orchestrates voice-input transcription: validate the upload (format, size,
 * best-effort duration) -> stage it to a temp file -> call {@link WhisperClient}
 * -> optionally persist a permanent copy -> always delete the temp file.
 * Never persists the recording unless {@code studybuddy.audio.persist-recordings}
 * is explicitly true (default false).
 */
@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);
    private static final String FEATURE = "audio";

    /** Formats OpenAI's transcription endpoint accepts. */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");

    /**
     * Conservative floor for the file-size-based duration estimate (~16kbps):
     * low enough that no legitimate recording at a typical-or-higher bitrate
     * is ever falsely rejected, while still catching obviously oversized
     * non-WAV uploads. See {@link AudioDurationEstimator}.
     */
    private static final long ASSUMED_MIN_BYTES_PER_SECOND = 2_000;

    private final WhisperClient whisperClient;
    private final AudioProperties properties;
    private final StudyBuddyMetrics metrics;

    public AudioTranscriptionService(WhisperClient whisperClient, AudioProperties properties, StudyBuddyMetrics metrics) {
        this.whisperClient = whisperClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    public AudioTranscriptionResult transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AudioProcessingException("Uploaded audio file is empty");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedAudioFormatException(
                    "Unsupported audio format for '" + filename + "'. Supported: " + ALLOWED_EXTENSIONS);
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new AudioTooLargeException(
                    "Recording exceeds the maximum file size of " + properties.maxFileSizeBytes() + " bytes");
        }

        byte[] bytes = readBytes(file);
        validateEstimatedDuration(bytes, extension, filename);

        Path tempFile = stageTempFile(bytes, extension);
        try {
            if (properties.persistRecordings()) {
                persistRecording(tempFile, filename);
            }
            return callWhisper(bytes, filename, resolveMimeType(file, extension));
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private void validateEstimatedDuration(byte[] bytes, String extension, String filename) {
        double estimatedSeconds;
        if (extension.equals("wav")) {
            OptionalDouble exact = AudioDurationEstimator.tryParseWavDurationSeconds(bytes);
            estimatedSeconds = exact.orElse(AudioDurationEstimator.estimateSecondsFromFileSize(bytes.length, ASSUMED_MIN_BYTES_PER_SECOND));
        } else {
            estimatedSeconds = AudioDurationEstimator.estimateSecondsFromFileSize(bytes.length, ASSUMED_MIN_BYTES_PER_SECOND);
        }

        if (estimatedSeconds > properties.maxDurationSeconds()) {
            throw new AudioTooLargeException(
                    "Recording for '" + filename + "' is estimated at " + Math.round(estimatedSeconds)
                            + "s, exceeding the maximum of " + properties.maxDurationSeconds() + "s");
        }
    }

    private AudioTranscriptionResult callWhisper(byte[] bytes, String filename, String mimeType) {
        long startNanos = System.nanoTime();
        try {
            WhisperTranscriptionResult result = whisperClient.transcribe(bytes, filename, mimeType);
            metrics.recordClaudeLatency(FEATURE, Duration.ofNanos(System.nanoTime() - startNanos));
            log.info("audio-transcription durationMs={} whisperDurationSeconds={}",
                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis(), result.durationSeconds());
            return new AudioTranscriptionResult(result.text(), result.language(), result.durationSeconds());
        } catch (WhisperClientTimeoutException e) {
            metrics.incrementModelFailure(FEATURE);
            throw new AudioTranscriptionTimeoutException("Whisper transcription call timed out", e);
        } catch (WhisperClientException e) {
            metrics.incrementModelFailure(FEATURE);
            throw new AudioTranscriptionException("Whisper transcription call failed", e);
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AudioProcessingException("Failed to read uploaded audio file", e);
        }
    }

    private Path stageTempFile(byte[] bytes, String extension) {
        try {
            Path directory = StringUtils.hasText(properties.tempDirectory())
                    ? ensureDirectory(Path.of(properties.tempDirectory()))
                    : null;
            Path tempFile = directory != null
                    ? Files.createTempFile(directory, "study-buddy-audio-", "." + extension)
                    : Files.createTempFile("study-buddy-audio-", "." + extension);
            Files.write(tempFile, bytes);
            return tempFile;
        } catch (IOException e) {
            throw new AudioProcessingException("Failed to stage uploaded audio file for processing", e);
        }
    }

    private void persistRecording(Path tempFile, String originalFilename) {
        try {
            Path directory = ensureDirectory(Path.of(properties.recordingsDirectory()));
            String persistedName = UUID.randomUUID() + "-" + originalFilename;
            Files.copy(tempFile, directory.resolve(persistedName));
            log.info("audio-recording persisted (persistRecordings=true)");
        } catch (IOException e) {
            // Persistence is a best-effort side feature; never fail the actual
            // transcription because a debugging copy couldn't be written.
            log.warn("Failed to persist audio recording copy", e);
        }
    }

    private static Path ensureDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        return directory;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temporary audio file {}", path, e);
        }
    }

    private static String sanitizeFilename(String originalFilename) {
        String cleaned = StringUtils.hasText(originalFilename)
                ? StringUtils.cleanPath(originalFilename)
                : "recording";
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1
                ? filename.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private static String resolveMimeType(MultipartFile file, String extension) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        return switch (extension) {
            case "mp3", "mpga" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "webm" -> "audio/webm";
            case "m4a" -> "audio/m4a";
            case "mp4" -> "audio/mp4";
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            default -> "application/octet-stream";
        };
    }
}
