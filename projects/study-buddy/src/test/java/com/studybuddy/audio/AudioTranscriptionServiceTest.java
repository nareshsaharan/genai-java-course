package com.studybuddy.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AudioTranscriptionServiceTest {

    @TempDir
    Path tempDir;

    private final WhisperClient whisperClient = mock(WhisperClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final StudyBuddyMetrics metrics = new StudyBuddyMetrics(meterRegistry);

    private AudioProperties properties(boolean persist) {
        return new AudioProperties(
                "sk-test-key", "whisper-1", 5_000_000, 60, 30, 2,
                persist, tempDir.resolve("recordings").toString(), tempDir.resolve("work").toString());
    }

    private AudioTranscriptionService service(boolean persist) {
        return new AudioTranscriptionService(whisperClient, properties(persist), metrics);
    }

    private static MockMultipartFile mp3File(byte[] content) {
        return new MockMultipartFile("file", "question.mp3", "audio/mpeg", content);
    }

    private static byte[] buildWav(int sampleRate, int durationSeconds) throws IOException {
        int dataSize = sampleRate * (durationSeconds);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes());
        writeU32(out, 36 + dataSize);
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        writeU32(out, 16);
        writeU16(out, 1);
        writeU16(out, 1);
        writeU32(out, sampleRate);
        writeU32(out, sampleRate);
        writeU16(out, 1);
        writeU16(out, 8);
        out.write("data".getBytes());
        writeU32(out, dataSize);
        out.write(new byte[dataSize]);
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 24) & 0xFF));
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    @Test
    void validTranscriptionSucceedsAndMapsWhisperResult() {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("What is dependency injection?", "english", 4.2));

        AudioTranscriptionResult result = service(false).transcribe(mp3File("fake mp3 bytes".getBytes()));

        assertThat(result.transcript()).isEqualTo("What is dependency injection?");
        assertThat(result.language()).isEqualTo("english");
        assertThat(result.durationSeconds()).isEqualTo(4.2);
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "question.mp3", "audio/mpeg", new byte[0]);

        assertThatThrownBy(() -> service(false).transcribe(empty))
                .isInstanceOf(AudioProcessingException.class);
    }

    @Test
    void unsupportedFormatIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "question.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> service(false).transcribe(file))
                .isInstanceOf(UnsupportedAudioFormatException.class);
    }

    @Test
    void fileExceedingMaxSizeIsRejected() {
        AudioProperties tinyLimit = new AudioProperties(
                "sk-test-key", "whisper-1", 10, 60, 30, 2, false,
                tempDir.resolve("recordings").toString(), tempDir.resolve("work").toString());
        AudioTranscriptionService service = new AudioTranscriptionService(whisperClient, tinyLimit, metrics);

        assertThatThrownBy(() -> service.transcribe(mp3File("this is way more than 10 bytes".getBytes())))
                .isInstanceOf(AudioTooLargeException.class);
    }

    @Test
    void wavFileExceedingMaxDurationIsRejected() throws IOException {
        AudioProperties shortLimit = new AudioProperties(
                "sk-test-key", "whisper-1", 5_000_000, 5, 30, 2, false,
                tempDir.resolve("recordings").toString(), tempDir.resolve("work").toString());
        AudioTranscriptionService service = new AudioTranscriptionService(whisperClient, shortLimit, metrics);
        MockMultipartFile longWav = new MockMultipartFile("file", "question.wav", "audio/wav", buildWav(8000, 10));

        assertThatThrownBy(() -> service.transcribe(longWav))
                .isInstanceOf(AudioTooLargeException.class);
    }

    @Test
    void wavFileWithinMaxDurationIsAccepted() throws IOException {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("hi", "english", 3.0));
        MockMultipartFile shortWav = new MockMultipartFile("file", "question.wav", "audio/wav", buildWav(8000, 3));

        AudioTranscriptionResult result = service(false).transcribe(shortWav);

        assertThat(result.transcript()).isEqualTo("hi");
    }

    @Test
    void whisperTimeoutIsWrappedAsAudioTranscriptionTimeoutException() {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenThrow(new WhisperClientTimeoutException("timed out", new RuntimeException()));

        assertThatThrownBy(() -> service(false).transcribe(mp3File("bytes".getBytes())))
                .isInstanceOf(AudioTranscriptionTimeoutException.class);
    }

    @Test
    void whisperFailureIsWrappedAsAudioTranscriptionException() {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenThrow(new WhisperClientException("boom", new RuntimeException()));

        assertThatThrownBy(() -> service(false).transcribe(mp3File("bytes".getBytes())))
                .isInstanceOf(AudioTranscriptionException.class)
                .isNotInstanceOf(AudioTranscriptionTimeoutException.class);
    }

    @Test
    void temporaryFileIsDeletedAfterProcessing() throws IOException {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("hi", "english", 1.0));

        service(false).transcribe(mp3File("bytes".getBytes()));

        Path workDir = tempDir.resolve("work");
        if (Files.exists(workDir)) {
            try (Stream<Path> files = Files.list(workDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void temporaryFileIsDeletedEvenWhenWhisperCallFails() throws IOException {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenThrow(new WhisperClientException("boom", new RuntimeException()));

        assertThatThrownBy(() -> service(false).transcribe(mp3File("bytes".getBytes())))
                .isInstanceOf(AudioTranscriptionException.class);

        Path workDir = tempDir.resolve("work");
        if (Files.exists(workDir)) {
            try (Stream<Path> files = Files.list(workDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void recordingIsNotPersistedByDefault() throws IOException {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("hi", "english", 1.0));

        service(false).transcribe(mp3File("bytes".getBytes()));

        Path recordingsDir = tempDir.resolve("recordings");
        assertThat(Files.exists(recordingsDir)).isFalse();
    }

    @Test
    void recordingIsPersistedWhenExplicitlyConfigured() throws IOException {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("hi", "english", 1.0));

        service(true).transcribe(mp3File("bytes".getBytes()));

        Path recordingsDir = tempDir.resolve("recordings");
        assertThat(Files.exists(recordingsDir)).isTrue();
        try (Stream<Path> files = Files.list(recordingsDir)) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void whisperClientIsInvokedWithOriginalFilenameAndMimeType() {
        when(whisperClient.transcribe(any(), anyString(), anyString()))
                .thenReturn(new WhisperTranscriptionResult("hi", "english", 1.0));

        service(false).transcribe(mp3File("bytes".getBytes()));

        verify(whisperClient).transcribe(any(), org.mockito.ArgumentMatchers.eq("question.mp3"), org.mockito.ArgumentMatchers.eq("audio/mpeg"));
    }
}
