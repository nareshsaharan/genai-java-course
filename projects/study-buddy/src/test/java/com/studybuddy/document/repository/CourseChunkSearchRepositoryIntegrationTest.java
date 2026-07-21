package com.studybuddy.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Exercises real cosine-similarity search against a real pgvector database:
 * requirement 5 ("retrieval returns the expected chunk") and requirement 6
 * ("weak similarity causes fallback"), both at the repository level rather
 * than through a mocked search in a service test.
 */
@SpringBootTest
@Testcontainers
class CourseChunkSearchRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void vectorStoreProperties(DynamicPropertyRegistry registry) {
        registry.add("studybuddy.database.host", postgres::getHost);
        registry.add("studybuddy.database.port", () -> postgres.getMappedPort(5432));
        registry.add("studybuddy.database.database", postgres::getDatabaseName);
        registry.add("studybuddy.database.username", postgres::getUsername);
        registry.add("studybuddy.database.password", postgres::getPassword);
        registry.add("studybuddy.claude.api-key", () -> "test-placeholder-key");
    }

    @Autowired
    private CourseChunkRepository courseChunkRepository;

    @Autowired
    private CourseChunkSearchRepository searchRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void returnsTheExpectedChunkForAClearlyRelevantQuery() {
        String relevantContent = "Dependency injection is a pattern where an object's dependencies "
                + "are supplied by an external container rather than constructed by the object itself.";
        String unrelatedContent = "Big O notation describes how an algorithm's running time grows with input size.";

        UUID expectedChunkId = UUID.randomUUID();
        courseChunkRepository.insertAll(List.of(
                new CourseChunkRecord(expectedChunkId, UUID.randomUUID(), relevantContent,
                        embeddingModel.embed(relevantContent).content().vector(),
                        "spring-notes.pdf", "Spring Boot", 0, Instant.now()),
                new CourseChunkRecord(UUID.randomUUID(), UUID.randomUUID(), unrelatedContent,
                        embeddingModel.embed(unrelatedContent).content().vector(),
                        "algorithms-notes.pdf", "Algorithms", 0, Instant.now())));

        float[] queryEmbedding = embeddingModel.embed("Explain dependency injection").content().vector();
        List<ChunkSearchResult> results = searchRepository.search(queryEmbedding, null, 5, 0.0);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo(expectedChunkId);
        assertThat(results.get(0).sourceFile()).isEqualTo("spring-notes.pdf");
    }

    @Test
    void weakSimilarityBelowThresholdIsFilteredOut() {
        String unrelatedContent = "The French Revolution began in 1789 and reshaped European politics.";
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                UUID.randomUUID(), UUID.randomUUID(), unrelatedContent,
                embeddingModel.embed(unrelatedContent).content().vector(),
                "history-notes.pdf", "History", 0, Instant.now())));

        float[] queryEmbedding = embeddingModel.embed(
                "What is the time complexity of binary search?").content().vector();

        // A high floor (0.6, the application default) filters out a genuinely
        // unrelated chunk, causing the caller to fall back to NO_RELEVANT_CONTEXT.
        List<ChunkSearchResult> results = searchRepository.search(queryEmbedding, null, 5, 0.6);

        assertThat(results).isEmpty();
    }

    @Test
    void topicFilterExcludesChunksFromOtherTopics() {
        String content = "Recursion is a function calling itself to solve smaller subproblems.";
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                UUID.randomUUID(), UUID.randomUUID(), content,
                embeddingModel.embed(content).content().vector(),
                "recursion-notes.pdf", "Algorithms", 0, Instant.now())));

        float[] queryEmbedding = embeddingModel.embed("Explain recursion").content().vector();

        List<ChunkSearchResult> resultsForWrongTopic = searchRepository.search(queryEmbedding, "Spring Boot", 5, 0.0);
        List<ChunkSearchResult> resultsForRightTopic = searchRepository.search(queryEmbedding, "Algorithms", 5, 0.0);

        assertThat(resultsForWrongTopic).isEmpty();
        assertThat(resultsForRightTopic).hasSize(1);
    }
}
