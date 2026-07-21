package com.studybuddy.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.studybuddy.document.repository.CourseChunkRecord;
import com.studybuddy.document.repository.CourseChunkRepository;
import com.studybuddy.tutor.dto.TutorChatRequest;
import com.studybuddy.tutor.dto.TutorChatResponse;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Exercises the real retrieval path (real Postgres + pgvector + real local
 * embedding model) end-to-end. TutorAssistant — the only component that
 * would call the real Claude API — is mocked, so this test never consumes
 * API credits.
 */
@SpringBootTest
@Testcontainers
class TutorChatIntegrationTest {

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
    private TutorChatService tutorChatService;

    @Autowired
    private CourseChunkRepository courseChunkRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private TutorAssistant tutorAssistant;

    @Test
    void retrievesRealChunksAndDelegatesToMockedAssistant() {
        when(tutorAssistant.answer(any())).thenReturn("DI lets dependencies be provided externally.");

        String content = "Dependency injection is a pattern where an object's dependencies "
                + "are supplied by an external container rather than constructed by the object itself.";
        UUID documentId = UUID.randomUUID();
        courseChunkRepository.insertAll(java.util.List.of(new CourseChunkRecord(
                UUID.randomUUID(), documentId, content,
                embeddingModel.embed(content).content().vector(),
                "spring-notes.pdf", "Spring Boot", 8, Instant.now())));

        TutorChatResponse response = tutorChatService.chat(
                new TutorChatRequest("Explain dependency injection", "Spring Boot"));

        assertThat(response.answer()).isEqualTo("DI lets dependencies be provided externally.");
        assertThat(response.confidence()).isNotEqualTo(Confidence.NO_RELEVANT_CONTEXT);
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).sourceFile()).isEqualTo("spring-notes.pdf");
    }

    @Test
    void unrelatedQuestionReturnsNoRelevantContextWithoutCallingAssistant() {
        String content = "Recursion is a function calling itself to solve smaller subproblems.";
        UUID documentId = UUID.randomUUID();
        courseChunkRepository.insertAll(java.util.List.of(new CourseChunkRecord(
                UUID.randomUUID(), documentId, content,
                embeddingModel.embed(content).content().vector(),
                "recursion-notes.txt", "Algorithms", 0, Instant.now())));

        TutorChatResponse response = tutorChatService.chat(
                new TutorChatRequest("What is the boiling point of mercury in degrees Celsius?", "Algorithms"));

        assertThat(response.confidence()).isEqualTo(Confidence.NO_RELEVANT_CONTEXT);
        assertThat(response.sources()).isEmpty();
        verify(tutorAssistant, never()).answer(any());
    }
}
