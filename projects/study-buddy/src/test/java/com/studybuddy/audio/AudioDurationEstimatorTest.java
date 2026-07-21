package com.studybuddy.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class AudioDurationEstimatorTest {

    /** Builds a minimal valid PCM WAV file: mono, given sample rate, 8-bit, silent. */
    private static byte[] buildWav(int sampleRate, int durationSeconds) throws IOException {
        int channels = 1;
        int bitsPerSample = 8;
        int dataSize = sampleRate * channels * (bitsPerSample / 8) * durationSeconds;
        int byteRate = sampleRate * channels * (bitsPerSample / 8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeUInt32LE(out, 36 + dataSize);
        out.write("WAVE".getBytes(StandardCharsets.US_ASCII));

        out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
        writeUInt32LE(out, 16);
        writeUInt16LE(out, 1); // PCM
        writeUInt16LE(out, channels);
        writeUInt32LE(out, sampleRate);
        writeUInt32LE(out, byteRate);
        writeUInt16LE(out, channels * (bitsPerSample / 8)); // block align
        writeUInt16LE(out, bitsPerSample);

        out.write("data".getBytes(StandardCharsets.US_ASCII));
        writeUInt32LE(out, dataSize);
        out.write(new byte[dataSize]);

        return out.toByteArray();
    }

    private static void writeUInt32LE(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private static void writeUInt16LE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    @Test
    void parsesExactDurationFromAValidWavHeader() throws IOException {
        byte[] wav = buildWav(8000, 3);

        OptionalDouble duration = AudioDurationEstimator.tryParseWavDurationSeconds(wav);

        assertThat(duration).isPresent();
        assertThat(duration.getAsDouble()).isCloseTo(3.0, offset(0.01));
    }

    @Test
    void parsesDurationRegardlessOfSampleRate() throws IOException {
        byte[] wav = buildWav(44100, 2);

        OptionalDouble duration = AudioDurationEstimator.tryParseWavDurationSeconds(wav);

        assertThat(duration).isPresent();
        assertThat(duration.getAsDouble()).isCloseTo(2.0, offset(0.01));
    }

    @Test
    void returnsEmptyForNonWavBytes() {
        byte[] notWav = "this is definitely not a wav file".getBytes(StandardCharsets.UTF_8);

        assertThat(AudioDurationEstimator.tryParseWavDurationSeconds(notWav)).isEmpty();
    }

    @Test
    void returnsEmptyForTooShortByteArray() {
        assertThat(AudioDurationEstimator.tryParseWavDurationSeconds(new byte[]{1, 2, 3})).isEmpty();
    }

    @Test
    void estimatesFromFileSizeUsingAssumedBytesPerSecond() {
        double estimate = AudioDurationEstimator.estimateSecondsFromFileSize(16_000, 2_000);

        assertThat(estimate).isCloseTo(8.0, offset(0.01));
    }
}
