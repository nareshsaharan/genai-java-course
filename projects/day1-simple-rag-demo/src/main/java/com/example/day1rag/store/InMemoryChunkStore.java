package com.example.day1rag.store;

import com.example.day1rag.model.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stores chunks in memory, grouped by the document they came from.
 *
 * Same idea as InMemoryDocumentStore: just a Map, nothing saved to disk.
 * In a later lesson, this is where embeddings will be attached to each
 * chunk and where top-k similarity search will run.
 */
@Component
public class InMemoryChunkStore {

    private final Map<String, List<DocumentChunk>> chunksByDocumentId = new ConcurrentHashMap<>();

    public void saveAll(String documentId, List<DocumentChunk> chunks) {
        chunksByDocumentId.put(documentId, new ArrayList<>(chunks));
    }

    public List<DocumentChunk> findByDocumentId(String documentId) {
        return chunksByDocumentId.getOrDefault(documentId, List.of());
    }
}
