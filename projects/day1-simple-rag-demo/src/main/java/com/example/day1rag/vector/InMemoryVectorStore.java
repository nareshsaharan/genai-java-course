package com.example.day1rag.vector;

import com.example.day1rag.model.VectorRecord;
import com.example.day1rag.service.EmbeddingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Stores every chunk's embedding in memory and can search over them.
 *
 * This is the "vector database" of our demo — just a List, nothing
 * fancy. Real vector databases (e.g. Pinecone, Weaviate, pgvector) do
 * the same conceptual job at a much larger scale, with fast approximate
 * search built in. Here, search is a simple, honest brute-force scan:
 * we compare the query against every single stored vector. That's fine
 * for a classroom demo with a handful of documents; a real system needs
 * smarter indexing once there are millions of vectors.
 */
@Component
public class InMemoryVectorStore {

    // CopyOnWriteArrayList is a simple way to make a List thread-safe
    // for our case: writes (ingesting documents) are rare, reads
    // (searching) are frequent — a good fit for this collection type.
    private final List<VectorRecord> records = new CopyOnWriteArrayList<>();

    private final EmbeddingService embeddingService;
    private final CosineSimilarity cosineSimilarity;

    public InMemoryVectorStore(EmbeddingService embeddingService, CosineSimilarity cosineSimilarity) {
        this.embeddingService = embeddingService;
        this.cosineSimilarity = cosineSimilarity;
    }

    public void add(VectorRecord record) {
        records.add(record);
    }

    /**
     * Finds the topK stored chunks most similar to the given query.
     *
     * Search flow:
     *   1. Convert the query text into an embedding, the same way every
     *      chunk was embedded during ingestion.
     *   2. Compare the query vector against every stored chunk vector
     *      using cosine similarity.
     *   3. Sort all the results by score, highest (most similar) first.
     *   4. Return only the top K results.
     */
    public List<ScoredVectorRecord> search(String query, int topK) {
        double[] queryVector = embeddingService.embed(query);

        List<ScoredVectorRecord> scored = new ArrayList<>();
        for (VectorRecord record : records) {
            double score = cosineSimilarity.calculate(queryVector, record.getVector());
            scored.add(new ScoredVectorRecord(record, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredVectorRecord::getScore).reversed());

        if (scored.size() > topK) {
            return scored.subList(0, topK);
        }
        return scored;
    }
}
