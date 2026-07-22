package com.studybuddy.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studybuddy.common.exception.NoRelevantContextException;
import com.studybuddy.common.exception.QuizGenerationException;
import com.studybuddy.common.exception.QuizGenerationTimeoutException;
import com.studybuddy.common.exception.QuizSubmissionException;
import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.config.properties.RagProperties;
import com.studybuddy.document.repository.ChunkSearchResult;
import com.studybuddy.document.repository.CourseChunkSearchRepository;
import com.studybuddy.flashcard.Difficulty;
import com.studybuddy.observability.StudyBuddyMetrics;
import com.studybuddy.progress.ProgressService;
import com.studybuddy.quiz.dto.QuizAnswerSubmission;
import com.studybuddy.quiz.dto.QuizGenerateRequest;
import com.studybuddy.quiz.dto.QuizGenerateResponse;
import com.studybuddy.quiz.dto.QuizSubmitRequest;
import com.studybuddy.quiz.dto.QuizSubmitResponse;
import com.studybuddy.quiz.repository.QuizAttemptRecord;
import com.studybuddy.quiz.repository.QuizAttemptRepository;
import com.studybuddy.quiz.repository.QuizQuestionRecord;
import com.studybuddy.quiz.repository.QuizRecord;
import com.studybuddy.quiz.repository.QuizRepository;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class QuizServiceTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CourseChunkSearchRepository searchRepository = mock(CourseChunkSearchRepository.class);
    private final QuizGenerator quizGenerator = mock(QuizGenerator.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizAttemptRepository quizAttemptRepository = mock(QuizAttemptRepository.class);
    private final ProgressService progressService = mock(ProgressService.class);
    private final RagProperties ragProperties = new RagProperties(400, 40, 5, 0.6);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final StudyBuddyMetrics metrics = new StudyBuddyMetrics(meterRegistry);
    private final RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);

    private final QuizService service = new QuizService(
            embeddingModel, searchRepository, quizGenerator, quizRepository, quizAttemptRepository,
            progressService, ragProperties, metrics, secrets);

    {
        when(secrets.getOpenAiKey()).thenReturn("sk-test-key-configured");
    }

    private ChunkSearchResult someChunk() {
        return new ChunkSearchResult(UUID.randomUUID(), "RAG combines retrieval with generation.", "rag-notes.pdf", 3, 0.9);
    }

    private void stubEmbeddingAndSearch(List<ChunkSearchResult> results) {
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(searchRepository.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(results);
    }

    private GeneratedQuizQuestion validQuestion(String question) {
        return new GeneratedQuizQuestion(question, List.of("A", "B", "C", "D"), 2);
    }

    // ---------- generate() ----------

    @Test
    void validStructuredResponseIsPersistedAndReturnedWithoutCorrectAnswers() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(List.of(
                validQuestion("What is RAG?"))));

        QuizGenerateResponse response = service.generate(new QuizGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.quizId()).isNotNull();
        assertThat(response.topic()).isEqualTo("RAG");
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).questionText()).isEqualTo("What is RAG?");
        assertThat(response.questions().get(0).options()).containsExactly("A", "B", "C", "D");

        ArgumentCaptor<QuizRecord> captor = ArgumentCaptor.forClass(QuizRecord.class);
        verify(quizRepository).save(captor.capture());
        assertThat(captor.getValue().questions()).hasSize(1);
        assertThat(captor.getValue().questions().get(0).correctOptionIndex()).isEqualTo(2);
    }

    @Test
    void missingContextThrowsWithoutCallingModel() {
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(searchRepository.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new QuizGenerateRequest("Quantum Computing", 5, Difficulty.MEDIUM)))
                .isInstanceOf(NoRelevantContextException.class);

        verify(quizGenerator, never()).generate(any());
        verify(quizRepository, never()).save(any());
    }

    @Test
    void mockModeDropsTheSimilarityFloorToZeroWhenNoOpenAiKeyIsConfigured() {
        when(secrets.getOpenAiKey()).thenReturn(null);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(searchRepository.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new QuizGenerateRequest("Quantum Computing", 5, Difficulty.MEDIUM)))
                .isInstanceOf(NoRelevantContextException.class);

        verify(searchRepository).search(any(), anyString(), anyInt(), org.mockito.ArgumentMatchers.eq(0.0));
    }

    @Test
    void modelTimeoutIsWrappedAsQuizGenerationTimeoutException() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(quizGenerator.generate(any())).thenThrow(new TimeoutException("timed out"));

        assertThatThrownBy(() -> service.generate(new QuizGenerateRequest("RAG", 5, Difficulty.MEDIUM)))
                .isInstanceOf(QuizGenerationTimeoutException.class);
    }

    @Test
    void malformedModelResponseIsWrappedAsQuizGenerationException() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(quizGenerator.generate(any())).thenThrow(new LangChain4jException("could not parse tool arguments"));

        assertThatThrownBy(() -> service.generate(new QuizGenerateRequest("RAG", 5, Difficulty.MEDIUM)))
                .isInstanceOf(QuizGenerationException.class);
    }

    @Test
    void invalidQuestionsAreFilteredOut() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(List.of(
                validQuestion("What is RAG?"),
                new GeneratedQuizQuestion("   ", List.of("A", "B", "C", "D"), 0),
                new GeneratedQuizQuestion("Too few options", List.of("A"), 0),
                new GeneratedQuizQuestion("Bad index", List.of("A", "B"), 5))));

        QuizGenerateResponse response = service.generate(new QuizGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).questionText()).isEqualTo("What is RAG?");
    }

    @Test
    void resultIsTruncatedToRequestedCount() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(quizGenerator.generate(any())).thenReturn(new GeneratedQuizBatch(List.of(
                validQuestion("Q1"), validQuestion("Q2"), validQuestion("Q3"))));

        QuizGenerateResponse response = service.generate(new QuizGenerateRequest("RAG", 2, Difficulty.MEDIUM));

        assertThat(response.questions()).hasSize(2);
    }

    // ---------- submit() ----------

    private QuizRecord quizWithTwoQuestions(UUID quizId, UUID q1, UUID q2) {
        return new QuizRecord(quizId, "RAG", "MEDIUM", Instant.now(), List.of(
                new QuizQuestionRecord(q1, quizId, 0, "Q1?", List.of("A", "B"), 0, null),
                new QuizQuestionRecord(q2, quizId, 1, "Q2?", List.of("A", "B"), 1, null)));
    }

    @Test
    void submitScoresAnswersPersistsAttemptAndUpdatesTopicProgress() {
        UUID quizId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quizWithTwoQuestions(quizId, q1, q2)));

        QuizSubmitRequest request = new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(q1, 0), // correct (correctOptionIndex=0)
                new QuizAnswerSubmission(q2, 0)  // wrong (correctOptionIndex=1)
        ));

        QuizSubmitResponse response = service.submit(quizId, request);

        assertThat(response.topic()).isEqualTo("RAG");
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.accuracy()).isEqualTo(0.5);
        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).anySatisfy(r -> {
            assertThat(r.questionId()).isEqualTo(q1);
            assertThat(r.correct()).isTrue();
            assertThat(r.correctOptionIndex()).isEqualTo(0);
        });

        ArgumentCaptor<QuizAttemptRecord> attemptCaptor = ArgumentCaptor.forClass(QuizAttemptRecord.class);
        verify(quizAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().correctCount()).isEqualTo(1);
        assertThat(attemptCaptor.getValue().totalCount()).isEqualTo(2);
        assertThat(attemptCaptor.getValue().answers()).hasSize(2);

        verify(progressService).recordAttempt("RAG", 1, 2);
    }

    @Test
    void submitAgainstUnknownQuizThrowsResourceNotFound() {
        UUID quizId = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(quizId, new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(UUID.randomUUID(), 0)))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitWithWrongQuestionCountThrowsQuizSubmissionException() {
        UUID quizId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quizWithTwoQuestions(quizId, q1, q2)));

        assertThatThrownBy(() -> service.submit(quizId, new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(q1, 0)))))
                .isInstanceOf(QuizSubmissionException.class);
    }

    @Test
    void submitWithUnknownQuestionIdThrowsQuizSubmissionException() {
        UUID quizId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quizWithTwoQuestions(quizId, q1, q2)));

        assertThatThrownBy(() -> service.submit(quizId, new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(q1, 0),
                new QuizAnswerSubmission(UUID.randomUUID(), 0)))))
                .isInstanceOf(QuizSubmissionException.class);
    }

    @Test
    void submitDelegatesTopicProgressUpdateEvenWhenAllAnswersCorrect() {
        UUID quizId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quizWithTwoQuestions(quizId, q1, q2)));

        service.submit(quizId, new QuizSubmitRequest(List.of(
                new QuizAnswerSubmission(q1, 0),
                new QuizAnswerSubmission(q2, 1))));

        verify(progressService).recordAttempt("RAG", 2, 2);
    }
}
