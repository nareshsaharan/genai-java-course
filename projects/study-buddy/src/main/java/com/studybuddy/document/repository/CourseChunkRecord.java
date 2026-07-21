package com.studybuddy.document.repository;

import java.time.Instant;
import java.util.UUID;

/** Row shape of the {@code course_chunks} table. */
public record CourseChunkRecord(
        UUID id,
        UUID documentId,
        String content,
        float[] embedding,
        String sourceFile,
        String topic,
        int chunkIndex,
        Instant createdAt
) {
}
