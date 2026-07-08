package com.example.day1rag.model;

/**
 * Represents a document exactly as it is stored inside our in-memory store.
 *
 * This is intentionally the same shape as the incoming request today
 * (documentId, title, content). We keep it as its own class — separate
 * from DocumentIngestRequest — because in later lessons the "stored"
 * version of a document will likely grow extra fields (e.g. a list of
 * chunk IDs) that the incoming request never needs to carry.
 */
public class DocumentRecord {

    private final String documentId;
    private final String title;
    private final String content;

    public DocumentRecord(String documentId, String title, String content) {
        this.documentId = documentId;
        this.title = title;
        this.content = content;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
