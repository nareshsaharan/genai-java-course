package com.example.day1rag.service;

import com.example.day1rag.model.DocumentChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Splits a document's text into smaller, overlapping pieces ("chunks").
 *
 * This is the simplest possible chunking strategy: cut the text every
 * N characters (chunkSize), and let the last part of each chunk repeat
 * at the start of the next one (overlap). It doesn't know about
 * sentences or paragraphs — that's a deliberate simplification for
 * Day 1. See the README for why overlap matters.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    // Read from application.properties: rag.chunk.size and rag.chunk.overlap
    private final int chunkSize;
    private final int overlap;

    public ChunkingService(
            @Value("${rag.chunk.size:500}") int chunkSize,
            @Value("${rag.chunk.overlap:100}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<DocumentChunk> chunk(String documentId, String title, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // How far to move forward for the next chunk. If overlap were
        // ever >= chunkSize, "step" would be zero or negative and we'd
        // never make progress — fall back to chunkSize in that case.
        int step = chunkSize - overlap;
        if (step <= 0) {
            step = chunkSize;
        }

        int chunkIndex = 0;
        int start = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String text = content.substring(start, end);

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("documentId", documentId);
            metadata.put("title", title);
            metadata.put("chunkIndex", String.valueOf(chunkIndex));
            metadata.put("source", documentId);

            DocumentChunk chunk = new DocumentChunk(
                    UUID.randomUUID().toString(),
                    documentId,
                    title,
                    chunkIndex,
                    text,
                    metadata
            );
            chunks.add(chunk);

            // Log the chunk index and a short preview so students can see
            // exactly how the text was split, without printing everything.
            String preview = text.length() > 100 ? text.substring(0, 100) : text;
            log.info("Chunk {} created: \"{}\"", chunkIndex, preview);

            chunkIndex++;
            start += step;
        }

        return chunks;
    }
}
