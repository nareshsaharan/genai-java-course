package com.studybuddy.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.studybuddy.document.dto.DocumentUploadResponse;

/**
 * End-to-end proof that ingestion actually lands correctly in Postgres:
 * a real pgvector/pgvector container, real Flyway migrations, real
 * embedding model, real JDBC inserts — nothing mocked.
 */
@SpringBootTest
@Testcontainers
class DocumentIngestionIntegrationTest {

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
    private DocumentIngestionService documentIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestsTxtFileAndPersistsDocumentAndChunks() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "algorithms.txt", "text/plain",
                ("Big O notation describes algorithm complexity. "
                        + "It is used to classify algorithms by how their running time grows.").getBytes());

        DocumentUploadResponse response = documentIngestionService.ingest(file, "Algorithms");

        assertThat(response.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(response.chunkCount()).isGreaterThan(0);

        Integer documentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Integer.class, response.documentId());
        assertThat(documentRows).isEqualTo(1);

        Integer chunkRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_chunks WHERE document_id = ?", Integer.class, response.documentId());
        assertThat(chunkRows).isEqualTo(response.chunkCount());

        Integer chunksWithEmbeddings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_chunks WHERE document_id = ? AND embedding IS NOT NULL",
                Integer.class, response.documentId());
        assertThat(chunksWithEmbeddings).isEqualTo(response.chunkCount());
    }

    @Test
    void reUploadingSameContentIsSkippedAsDuplicate() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "duplicate-notes.txt", "text/plain",
                "Recursion is a function calling itself to solve smaller subproblems.".getBytes());

        DocumentUploadResponse first = documentIngestionService.ingest(file, "Recursion");
        assertThat(first.status()).isEqualTo(IngestionStatus.INGESTED);

        MockMultipartFile sameContentDifferentName = new MockMultipartFile(
                "file", "duplicate-notes-copy.txt", "text/plain",
                "Recursion is a function calling itself to solve smaller subproblems.".getBytes());

        DocumentUploadResponse second = documentIngestionService.ingest(sameContentDifferentName, "Recursion");

        assertThat(second.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(second.documentId()).isEqualTo(first.documentId());

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Integer.class, first.documentId());
        assertThat(documentCount).isEqualTo(1);
    }
}
