package com.example.day1rag.service;

import com.example.day1rag.model.DocumentChunk;
import com.example.day1rag.model.DocumentIngestRequest;
import com.example.day1rag.model.DocumentRecord;
import com.example.day1rag.model.VectorRecord;
import com.example.day1rag.store.InMemoryChunkStore;
import com.example.day1rag.store.InMemoryDocumentStore;
import com.example.day1rag.vector.InMemoryVectorStore;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Holds the "business logic" for ingesting a document.
 *
 * As of today, ingestion does three things:
 *   1. Save the whole document as-is (useful for reference/debugging).
 *   2. Split it into chunks and save those too.
 *   3. Create a (fake) embedding for every chunk and add chunk + vector
 *      pairs into the vector store, ready for search (see RetrievalService).
 */
@Service
public class DocumentService {

    private final InMemoryDocumentStore documentStore;
    private final InMemoryChunkStore chunkStore;
    private final InMemoryVectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentService(InMemoryDocumentStore documentStore,
                            InMemoryChunkStore chunkStore,
                            InMemoryVectorStore vectorStore,
                            ChunkingService chunkingService,
                            EmbeddingService embeddingService) {
        this.documentStore = documentStore;
        this.chunkStore = chunkStore;
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    public int ingest(DocumentIngestRequest request) {
        DocumentRecord record = new DocumentRecord(
                request.getDocumentId(),
                request.getTitle(),
                request.getContent()
        );
        documentStore.save(record);

        List<DocumentChunk> chunks = chunkingService.chunk(
                request.getDocumentId(),
                request.getTitle(),
                request.getContent()
        );
        chunkStore.saveAll(request.getDocumentId(), chunks);

        // For every chunk, turn its text into a vector and add the pair
        // to the vector store. This is the "embeddings" step of RAG.
        for (DocumentChunk chunk : chunks) {
            double[] vector = embeddingService.embed(chunk.getText());
            vectorStore.add(new VectorRecord(chunk, vector));
        }

        return chunks.size();
    }
}
