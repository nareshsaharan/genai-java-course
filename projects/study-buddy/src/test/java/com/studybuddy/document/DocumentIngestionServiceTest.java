package com.studybuddy.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.studybuddy.common.exception.DocumentProcessingException;
import com.studybuddy.common.exception.UnsupportedFileTypeException;
import com.studybuddy.document.dto.DocumentUploadResponse;
import com.studybuddy.document.loader.DocumentTextLoader;
import com.studybuddy.document.loader.DocumentTextLoaderResolver;
import com.studybuddy.document.repository.CourseChunkRecord;
import com.studybuddy.document.repository.CourseChunkRepository;
import com.studybuddy.document.repository.DocumentRecord;
import com.studybuddy.document.repository.DocumentRepository;
import com.studybuddy.observability.StudyBuddyMetrics;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DocumentIngestionServiceTest {

    private final DocumentTextLoaderResolver loaderResolver = mock(DocumentTextLoaderResolver.class);
    private final TextChunker textChunker = mock(TextChunker.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final CourseChunkRepository courseChunkRepository = mock(CourseChunkRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final StudyBuddyMetrics metrics = new StudyBuddyMetrics(meterRegistry);

    private final DocumentIngestionService service = new DocumentIngestionService(
            loaderResolver, textChunker, embeddingModel, documentRepository, courseChunkRepository, metrics);

    private MockMultipartFile someFile() {
        return new MockMultipartFile("file", "notes.txt", "text/plain", "hello world".getBytes());
    }

    @Test
    void ingestsNewDocumentAndStoresChunksWithEmbeddings() throws IOException {
        DocumentTextLoader loader = mock(DocumentTextLoader.class);
        when(loaderResolver.resolve("notes.txt")).thenReturn(loader);
        when(loader.extractText(any())).thenReturn("hello world, this is course content");

        TextSegment segment1 = TextSegment.from("hello world,");
        TextSegment segment2 = TextSegment.from("this is course content");
        when(textChunker.chunk(anyString())).thenReturn(List.of(segment1, segment2));

        Embedding embedding1 = Embedding.from(new float[]{0.1f, 0.2f});
        Embedding embedding2 = Embedding.from(new float[]{0.3f, 0.4f});
        when(embeddingModel.embedAll(List.of(segment1, segment2)))
                .thenReturn(Response.from(List.of(embedding1, embedding2)));

        when(documentRepository.findByContentHash(anyString())).thenReturn(Optional.empty());

        DocumentUploadResponse response = service.ingest(someFile(), "Java Basics");

        assertThat(response.documentId()).isNotNull();
        assertThat(response.sourceFilename()).isEqualTo("notes.txt");
        assertThat(response.chunkCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(IngestionStatus.INGESTED);

        ArgumentCaptor<DocumentRecord> documentCaptor = ArgumentCaptor.forClass(DocumentRecord.class);
        verify(documentRepository).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().sourceFilename()).isEqualTo("notes.txt");
        assertThat(documentCaptor.getValue().topic()).isEqualTo("Java Basics");
        assertThat(documentCaptor.getValue().chunkCount()).isEqualTo(2);
        assertThat(documentCaptor.getValue().status()).isEqualTo(IngestionStatus.INGESTED.name());

        ArgumentCaptor<List<CourseChunkRecord>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseChunkRepository).insertAll(chunksCaptor.capture());
        List<CourseChunkRecord> chunks = chunksCaptor.getValue();
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isZero();
        assertThat(chunks.get(0).sourceFile()).isEqualTo("notes.txt");
        assertThat(chunks.get(0).topic()).isEqualTo("Java Basics");
        assertThat(chunks.get(0).embedding()).isEqualTo(embedding1.vector());
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks.get(0).documentId()).isEqualTo(response.documentId());

        assertThat(meterRegistry.get("studybuddy.ingestion.duration").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("studybuddy.ingestion.chunks").summary().totalAmount()).isEqualTo(2.0);
    }

    @Test
    void duplicateContentIsSkippedWithoutReEmbedding() throws IOException {
        DocumentTextLoader loader = mock(DocumentTextLoader.class);
        when(loaderResolver.resolve(anyString())).thenReturn(loader);
        when(loader.extractText(any())).thenReturn("hello world, this is course content");

        UUID existingId = UUID.randomUUID();
        DocumentRecord existing = new DocumentRecord(
                existingId, "old-name.txt", "Java Basics", "somehash", 3, "INGESTED", Instant.now());
        when(documentRepository.findByContentHash(anyString())).thenReturn(Optional.of(existing));

        DocumentUploadResponse response = service.ingest(someFile(), "Java Basics");

        assertThat(response.documentId()).isEqualTo(existingId);
        assertThat(response.sourceFilename()).isEqualTo("old-name.txt");
        assertThat(response.chunkCount()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(IngestionStatus.DUPLICATE);

        verify(embeddingModel, never()).embedAll(any());
        verify(documentRepository, never()).insert(any());
        verify(courseChunkRepository, never()).insertAll(any());
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.ingest(emptyFile, null))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void blankExtractedTextIsRejected() throws IOException {
        DocumentTextLoader loader = mock(DocumentTextLoader.class);
        when(loaderResolver.resolve(anyString())).thenReturn(loader);
        when(loader.extractText(any())).thenReturn("   \n  ");

        assertThatThrownBy(() -> service.ingest(someFile(), null))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void extractionFailureIsWrappedAsDocumentProcessingException() throws IOException {
        DocumentTextLoader loader = mock(DocumentTextLoader.class);
        when(loaderResolver.resolve(anyString())).thenReturn(loader);
        when(loader.extractText(any())).thenThrow(new IOException("corrupt file"));

        assertThatThrownBy(() -> service.ingest(someFile(), null))
                .isInstanceOf(DocumentProcessingException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void unsupportedFileTypePropagates() {
        when(loaderResolver.resolve(anyString()))
                .thenThrow(new UnsupportedFileTypeException("nope"));

        MockMultipartFile file = new MockMultipartFile("file", "notes.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> service.ingest(file, null))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }
}
