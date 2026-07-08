package com.example.day1rag.model;

import java.util.Map;

/**
 * A small piece of a document, ready to be embedded and searched later.
 *
 * Why split a document into chunks at all? Because in later lessons we
 * will convert text into embeddings (number vectors) and search over
 * them — that works much better on small, focused pieces of text than
 * on one giant document.
 *
 * The "metadata" map carries extra information about where this chunk
 * came from. Keeping it as a Map (instead of hardcoded fields) makes it
 * easy to attach more information later without changing this class.
 */
public class DocumentChunk {

    private final String chunkId;
    private final String documentId;
    private final String title;
    private final int chunkIndex;
    private final String text;
    private final Map<String, String> metadata;

    public DocumentChunk(String chunkId, String documentId, String title,
                          int chunkIndex, String text, Map<String, String> metadata) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.title = title;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.metadata = metadata;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getText() {
        return text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
