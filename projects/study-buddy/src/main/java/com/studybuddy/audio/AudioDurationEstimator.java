package com.studybuddy.audio;

import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;

/**
 * Best-effort duration checking, used only to reject an obviously-too-long
 * recording before spending a Whisper API call on it (Whisper's own
 * response carries the authoritative duration afterward).
 *
 * <p>Exact duration is only computed for WAV (its RIFF header states sample
 * rate/channels/bit depth/data size directly). Browsers' MediaRecorder
 * normally produces compressed formats (webm/opus, mp4/aac) that would need
 * a real codec/demuxer to measure precisely — out of scope here, so those
 * fall back to {@link #estimateSecondsFromFileSize}, a deliberately
 * conservative heuristic documented at the call site.
 */
final class AudioDurationEstimator {

    private static final int MIN_HEADER_SIZE = 12;

    private AudioDurationEstimator() {
    }

    static OptionalDouble tryParseWavDurationSeconds(byte[] bytes) {
        if (bytes.length < MIN_HEADER_SIZE || !matches(bytes, 0, "RIFF") || !matches(bytes, 8, "WAVE")) {
            return OptionalDouble.empty();
        }

        Integer sampleRate = null;
        Integer channels = null;
        Integer bitsPerSample = null;
        Long dataSize = null;

        int pos = 12;
        while (pos + 8 <= bytes.length) {
            String chunkId = new String(bytes, pos, 4, StandardCharsets.US_ASCII);
            long chunkSize = readUInt32LE(bytes, pos + 4);
            int chunkDataStart = pos + 8;
            if (chunkDataStart + chunkSize > bytes.length) {
                break;
            }

            if (chunkId.equals("fmt ") && chunkSize >= 16) {
                channels = readUInt16LE(bytes, chunkDataStart + 2);
                sampleRate = (int) readUInt32LE(bytes, chunkDataStart + 4);
                bitsPerSample = readUInt16LE(bytes, chunkDataStart + 14);
            } else if (chunkId.equals("data")) {
                dataSize = chunkSize;
            }

            long paddedSize = chunkSize + (chunkSize % 2);
            pos = (int) (chunkDataStart + paddedSize);

            if (sampleRate != null && channels != null && bitsPerSample != null && dataSize != null) {
                break;
            }
        }

        if (sampleRate == null || channels == null || bitsPerSample == null || dataSize == null
                || sampleRate == 0 || channels == 0 || bitsPerSample == 0) {
            return OptionalDouble.empty();
        }

        double bytesPerSecond = sampleRate * channels * (bitsPerSample / 8.0);
        return OptionalDouble.of(dataSize / bytesPerSecond);
    }

    static double estimateSecondsFromFileSize(long fileSizeBytes, long assumedBytesPerSecond) {
        return (double) fileSizeBytes / assumedBytesPerSecond;
    }

    private static boolean matches(byte[] bytes, int offset, String ascii) {
        return new String(bytes, offset, ascii.length(), StandardCharsets.US_ASCII).equals(ascii);
    }

    private static long readUInt32LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static int readUInt16LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}
