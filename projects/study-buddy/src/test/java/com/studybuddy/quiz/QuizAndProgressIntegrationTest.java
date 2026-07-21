package com.studybuddy.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.studybuddy.document.repository.CourseChunkRecord;
import com.studybuddy.document.repository.CourseChunkRepository;
import com.studybuddy.flashcard.Difficulty;
import com.studybuddy.progress.ProgressService;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.TopicProgressView;
import com.studybuddy.quiz.dto.QuizAnswerSubmission;
import com.studybuddy.quiz.dto.QuizGenerateRequest;
import com.studybuddy.quiz.dto.QuizGenerateResponse;
import com.studybuddy.quiz.dto.QuizSubmitRequest;
import com.studybuddy.quiz.dto.QuizSubmitResponse;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Exercises the real generate -> submit -> weak-topic-tracking path (real
 * Postgres + pgvector + real local embedding model + real JDBC inserts).
 * QuizGenerator — the only component that would call the real Claude API —
 * is mocked, so this test never consumes API credits.
 */
@SpringBootTest
@Testcontainers
class QuizAndProgressIntegrationTest {

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
    private QuizService quizService;

    @Autowired
    private ProgressService progressService;

    @Autowired
    private CourseChunkRepository courseChunkRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuizGenerator quizGenerator;

    @Test
    void generatingSubmittingAndTrackingWeakTopicsWorksEndToEnd() {
        String content = "Retrieval augmented generation (RAG) combines a retriever over a "
                + "vector store with a generative language model to ground answers in real documents.";
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                UUID.randomUUID(), UUID.randomUUID(), content,
                embeddingModel.embed(content).content().vector(),
                "rag-notes.pdf", "RAG", 0, Instant.now())));

        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(List.of(
                new GeneratedQuizQuestion("What does RAG combine?", List.of("A retriever and generator", "Two databases"), 0),
                new GeneratedQuizQuestion("What does RAG mitigate?", List.of("Hallucination", "Latency"), 0))));

        QuizGenerateResponse generated = quizService.generate(new QuizGenerateRequest("RAG", 2, Difficulty.MEDIUM));
        assertThat(generated.questions()).hasSize(2);
        assertThat(generated.questions()).allSatisfy(q -> assertThat(q.options()).isNotEmpty());

        Integer questionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_questions WHERE quiz_id = ?", Integer.class, generated.quizId());
        assertThat(questionRows).isEqualTo(2);

        // Answer one correctly, one incorrectly — 50% accuracy on this attempt.
        QuizSubmitResponse submitted = quizService.submit(generated.quizId(), new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(generated.questions().get(0).questionId(), 0),
                new QuizAnswerSubmission(generated.questions().get(1).questionId(), 1))));

        assertThat(submitted.correctCount()).isEqualTo(1);
        assertThat(submitted.totalCount()).isEqualTo(2);
        assertThat(submitted.results()).allSatisfy(r -> assertThat(r.correctOptionIndex()).isEqualTo(0));

        Integer attemptRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = ?", Integer.class, generated.quizId());
        assertThat(attemptRows).isEqualTo(1);

        List<TopicProgressView> topics = progressService.getTopics();
        assertThat(topics).anySatisfy(t -> {
            assertThat(t.topic()).isEqualTo("RAG");
            assertThat(t.totalCount()).isEqualTo(2);
            assertThat(t.correctCount()).isEqualTo(1);
        });
    }

    @Test
    void weakTopicBelowMinimumAttemptsIsNotYetClassifiedWeak() {
        String content = "Recursion is a function calling itself to solve smaller subproblems.";
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                UUID.randomUUID(), UUID.randomUUID(), content,
                embeddingModel.embed(content).content().vector(),
                "recursion-notes.txt", "Recursion", 0, Instant.now())));

        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(List.of(
                new GeneratedQuizQuestion("What is recursion?", List.of("Self-calling function", "A loop"), 0))));

        QuizGenerateResponse generated = quizService.generate(new QuizGenerateRequest("Recursion", 1, Difficulty.EASY));
        // Answer wrong — 0% accuracy, but only 1 question attempted (below the default min of 5).
        quizService.submit(generated.quizId(), new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(generated.questions().get(0).questionId(), 1))));

        List<TopicProgressView> weakTopics = progressService.getWeakTopics();
        assertThat(weakTopics).noneSatisfy(t -> assertThat(t.topic()).isEqualTo("Recursion"));
    }

    @Test
    void recommendationExplainsItsChoiceOnceEnoughDataExists() {
        String content = "Big O notation describes how an algorithm's running time grows with input size.";
        courseChunkRepository.insertAll(List.of(new CourseChunkRecord(
                UUID.randomUUID(), UUID.randomUUID(), content,
                embeddingModel.embed(content).content().vector(),
                "algorithms-notes.txt", "Algorithms", 0, Instant.now())));

        List<GeneratedQuizQuestion> fiveWrongQuestions = List.of(
                new GeneratedQuizQuestion("Q1", List.of("Right", "Wrong"), 0),
                new GeneratedQuizQuestion("Q2", List.of("Right", "Wrong"), 0),
                new GeneratedQuizQuestion("Q3", List.of("Right", "Wrong"), 0),
                new GeneratedQuizQuestion("Q4", List.of("Right", "Wrong"), 0),
                new GeneratedQuizQuestion("Q5", List.of("Right", "Wrong"), 0));
        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(fiveWrongQuestions));

        QuizGenerateResponse generated = quizService.generate(new QuizGenerateRequest("Algorithms", 5, Difficulty.HARD));
        // Answer all 5 incorrectly to clear the min-attempts floor with low accuracy.
        List<QuizAnswerSubmission> allWrong = generated.questions().stream()
                .map(q -> new QuizAnswerSubmission(q.questionId(), 1))
                .toList();
        quizService.submit(generated.quizId(), new QuizSubmitRequest(allWrong));

        RecommendationResponse recommendation = progressService.getRecommendation();
        assertThat(recommendation.topic()).isEqualTo("Algorithms");
        assertThat(recommendation.reason()).isNotBlank();
    }
}
