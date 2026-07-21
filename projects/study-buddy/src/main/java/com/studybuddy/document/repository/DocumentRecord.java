package com.studybuddy.document.repository;

import java.time.Instant;
import java.util.UUID;

/** Row shape of the {@code documents} table. */
public record DocumentRecord(
        UUID id,
        String sourceFilename,
        String topic,
        String contentHash,
        int chunkCount,
        String status,
        Instant createdAt
) {
}
