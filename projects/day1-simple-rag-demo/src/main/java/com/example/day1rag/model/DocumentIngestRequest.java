package com.example.day1rag.model;

/**
 * The JSON body a client sends to POST /api/documents/ingest.
 *
 * Example JSON:
 * {
 *   "documentId": "course-notes",
 *   "title": "GenAI Course Notes",
 *   "content": "RAG means Retrieval-Augmented Generation..."
 * }
 *
 * Beginners: Spring automatically converts incoming JSON into an object
 * of this class (this is called "deserialization"). We use a plain class
 * with simple getters/setters so it's easy to read — no fancy Lombok magic.
 */
public class DocumentIngestRequest {

    private String documentId;
    private String title;
    private String content;

    // Spring needs a no-args constructor to build this object from JSON.
    public DocumentIngestRequest() {
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
