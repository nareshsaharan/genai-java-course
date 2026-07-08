package com.example.day1rag.store;

import com.example.day1rag.model.DocumentRecord;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The simplest possible "database": a Map that lives in memory.
 *
 * Beginners: nothing here is saved to disk. If you restart the app,
 * every stored document disappears. That's fine for a demo — later
 * lessons will use this same idea (a Map keyed by an id) to build the
 * in-memory vector store too.
 *
 * We use ConcurrentHashMap instead of a plain HashMap because Spring
 * handles web requests on multiple threads at once, and ConcurrentHashMap
 * is safe to read/write from many threads without extra locking code.
 */
@Component
public class InMemoryDocumentStore {

    private final Map<String, DocumentRecord> documents = new ConcurrentHashMap<>();

    public void save(DocumentRecord document) {
        documents.put(document.getDocumentId(), document);
    }

    public DocumentRecord findById(String documentId) {
        return documents.get(documentId);
    }
}
