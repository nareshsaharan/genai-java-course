package com.studybuddy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.studybuddy.common.exception.NoRelevantContextException;
import com.studybuddy.document.repository.CourseChunkRecord;
import com.studybuddy.document.repository.CourseChunkRepository;
import com.studybuddy.flashcard.dto.FlashcardGenerateRequest;
import com.studybuddy.flashcard.dto.FlashcardGenerateResponse;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Exercises the real retrieval + persistence path (real Postgres + pgvector
 * + real local embedding model + real JDBC inserts). FlashcardGenerator —
 * the only component that would call the real Claude API — is mocked, so
 * this test never consumes API credits.
 */
@SpringBootTest
@Testcontainers
class FlashcardIntegrationTest {

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
    private FlashcardService flashcardService;

    @Autowired
    private CourseChunkRepository courseChunkRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FlashcardGenerator flashcardGenerator;

    @Test
    void generatesPersistsAndReturnsCardsWithRealRetrieval() {
        String content = "Retrieval augmented generation (RAG) combines a retriever over a "
                + "vector store with a generative language model to ground answers in real documents.";
        UUID chunkId = UUID.randomUUID();
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                chunkId, UUID.randomUUID(), content,
                embeddingModel.embed(content).content().vector(),
                "rag-notes.pdf", "RAG", 0, Instant.now())));

        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("What does RAG combine?", "A retriever and a generative model."))));

        FlashcardGenerateResponse response = flashcardService.generate(
                new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).sourceChunkIds()).contains(chunkId);

        Integer savedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flashcards WHERE id = ?", Integer.class, response.cards().get(0).id());
        assertThat(savedCount).isEqualTo(1);

        Integer sourceLinkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flashcard_sources WHERE flashcard_id = ? AND chunk_id = ?",
                Integer.class, response.cards().get(0).id(), chunkId);
        assertThat(sourceLinkCount).isEqualTo(1);
    }

    @Test
    void missingContextThrowsWithoutCallingModelOrPersisting() {
        assertThatThrownBy(() -> flashcardService.generate(
                        new FlashcardGenerateRequest("A Totally Unrelated Untaught Topic Xyz", 5, Difficulty.MEDIUM)))
                .isInstanceOf(NoRelevantContextException.class);

        verify(flashcardGenerator, never()).generate(any());
    }
}
