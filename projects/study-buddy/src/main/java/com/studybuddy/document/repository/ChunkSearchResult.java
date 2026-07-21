package com.studybuddy.document.repository;

import java.util.UUID;

/** One chunk returned by a similarity search, with its cosine similarity score. */
public record ChunkSearchResult(
        UUID id,
        String content,
        String sourceFile,
        int chunkIndex,
        double similarityScore
) {
}
