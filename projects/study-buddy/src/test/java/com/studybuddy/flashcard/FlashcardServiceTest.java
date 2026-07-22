package com.studybuddy.flashcard;

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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studybuddy.common.exception.FlashcardGenerationException;
import com.studybuddy.common.exception.FlashcardGenerationTimeoutException;
import com.studybuddy.common.exception.NoRelevantContextException;
import com.studybuddy.config.properties.RagProperties;
import com.studybuddy.document.repository.ChunkSearchResult;
import com.studybuddy.document.repository.CourseChunkSearchRepository;
import com.studybuddy.flashcard.dto.Flashcard;
import com.studybuddy.flashcard.dto.FlashcardGenerateRequest;
import com.studybuddy.flashcard.dto.FlashcardGenerateResponse;
import com.studybuddy.flashcard.repository.FlashcardRecord;
import com.studybuddy.flashcard.repository.FlashcardRepository;
import com.studybuddy.observability.StudyBuddyMetrics;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class FlashcardServiceTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CourseChunkSearchRepository searchRepository = mock(CourseChunkSearchRepository.class);
    private final FlashcardGenerator flashcardGenerator = mock(FlashcardGenerator.class);
    private final FlashcardRepository flashcardRepository = mock(FlashcardRepository.class);
    private final RagProperties ragProperties = new RagProperties(400, 40, 5, 0.6);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final StudyBuddyMetrics metrics = new StudyBuddyMetrics(meterRegistry);
    private final RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);

    private final FlashcardService service = new FlashcardService(
            embeddingModel, searchRepository, flashcardGenerator, flashcardRepository, ragProperties, metrics, secrets);

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

    @Test
    void validStructuredResponseIsSavedAndReturnedWithSourceChunkIds() {
        ChunkSearchResult chunk = someChunk();
        stubEmbeddingAndSearch(List.of(chunk));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("What is RAG?", "Retrieval augmented generation."))));

        FlashcardGenerateResponse response = service.generate(
                new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.cards()).hasSize(1);
        Flashcard card = response.cards().get(0);
        assertThat(card.id()).isNotNull();
        assertThat(card.question()).isEqualTo("What is RAG?");
        assertThat(card.answer()).isEqualTo("Retrieval augmented generation.");
        assertThat(card.topic()).isEqualTo("RAG");
        assertThat(card.difficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(card.sourceChunkIds()).containsExactly(chunk.id());

        ArgumentCaptor<List<FlashcardRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(flashcardRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).sourceChunkIds()).containsExactly(chunk.id());

        assertThat(meterRegistry.get("studybuddy.retrieval.latency").tag("feature", "flashcard").timer().count())
                .isEqualTo(1);
    }

    @Test
    void embedsAnEnrichedQueryRatherThanTheBareTopicString() {
        // A bare topic string like "RAG" embeds poorly against paragraph-length
        // chunk content (often scoring below the similarity floor even for
        // genuinely relevant chunks — confirmed against a real corpus). Embedding
        // a fuller phrase gives retrieval a much better chance of clearing it.
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("What is RAG?", "Retrieval augmented generation."))));

        service.generate(new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        ArgumentCaptor<String> embeddedTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingModel).embed(embeddedTextCaptor.capture());
        assertThat(embeddedTextCaptor.getValue())
                .isNotEqualTo("RAG")
                .contains("RAG");
        assertThat(meterRegistry.get("studybuddy.claude.latency").tag("feature", "flashcard").timer().count())
                .isEqualTo(1);
    }

    @Test
    void malformedModelResponseIsWrappedAsFlashcardGenerationException() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenThrow(new LangChain4jException("could not parse tool arguments"));

        assertThatThrownBy(() -> service.generate(new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM)))
                .isInstanceOf(FlashcardGenerationException.class);
        verify(flashcardRepository, never()).saveAll(any());
        assertThat(meterRegistry.get("studybuddy.model.failure.count").tag("feature", "flashcard").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void nullCardsListInBatchIsTreatedAsNoCardsGenerated() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(null));

        FlashcardGenerateResponse response = service.generate(
                new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.cards()).isEmpty();
        verify(flashcardRepository).saveAll(List.of());
    }

    @Test
    void modelTimeoutIsWrappedAsFlashcardGenerationTimeoutException() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenThrow(new TimeoutException("timed out"));

        assertThatThrownBy(() -> service.generate(new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM)))
                .isInstanceOf(FlashcardGenerationTimeoutException.class);
    }

    @Test
    void duplicateCardsAreRemovedBeforeSaving() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("What is RAG?", "Answer one"),
                new GeneratedFlashcard("What is RAG", "Answer two"))));

        FlashcardGenerateResponse response = service.generate(
                new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.cards()).hasSize(1);
    }

    @Test
    void blankQuestionsAndAnswersAreRejected() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("What is RAG?", "Retrieval augmented generation."),
                new GeneratedFlashcard("   ", "some answer"),
                new GeneratedFlashcard("some question", "   "))));

        FlashcardGenerateResponse response = service.generate(
                new FlashcardGenerateRequest("RAG", 5, Difficulty.MEDIUM));

        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).question()).isEqualTo("What is RAG?");
    }

    @Test
    void missingContextThrowsWithoutCallingModel() {
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(searchRepository.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new FlashcardGenerateRequest("Quantum Computing", 5, Difficulty.MEDIUM)))
                .isInstanceOf(NoRelevantContextException.class);

        verify(flashcardGenerator, never()).generate(any());
        verify(flashcardRepository, never()).saveAll(any());
        assertThat(meterRegistry.get("studybuddy.no_context.count").tag("feature", "flashcard").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void mockModeDropsTheSimilarityFloorToZeroWhenNoOpenAiKeyIsConfigured() {
        when(secrets.getOpenAiKey()).thenReturn(null);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(searchRepository.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new FlashcardGenerateRequest("Quantum Computing", 5, Difficulty.MEDIUM)))
                .isInstanceOf(NoRelevantContextException.class);

        verify(searchRepository).search(any(), anyString(), anyInt(), org.mockito.ArgumentMatchers.eq(0.0));
    }

    @Test
    void resultIsTruncatedToRequestedCount() {
        stubEmbeddingAndSearch(List.of(someChunk()));
        when(flashcardGenerator.generate(any())).thenReturn(new FlashcardBatch(List.of(
                new GeneratedFlashcard("Question one", "Answer one"),
                new GeneratedFlashcard("Question two", "Answer two"),
                new GeneratedFlashcard("Question three", "Answer three"))));

        FlashcardGenerateResponse response = service.generate(
                new FlashcardGenerateRequest("RAG", 2, Difficulty.MEDIUM));

        assertThat(response.cards()).hasSize(2);
    }
}
