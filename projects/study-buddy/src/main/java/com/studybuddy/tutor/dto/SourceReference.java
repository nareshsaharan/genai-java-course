package com.studybuddy.tutor.dto;

import java.util.UUID;

/**
 * API-facing view of a retrieved chunk. Deliberately not the course_chunks
 * database entity: only the fields a client needs to display/cite a source
 * are exposed, and the full chunk content is truncated to a snippet.
 */
public record SourceReference(
        UUID chunkId,
        String sourceFile,
        int chunkIndex,
        String snippet,
        double similarityScore
) {
}
