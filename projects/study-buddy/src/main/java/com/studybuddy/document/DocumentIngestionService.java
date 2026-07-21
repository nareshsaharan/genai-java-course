package com.studybuddy.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.studybuddy.common.exception.DocumentProcessingException;
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

/**
 * Orchestrates document ingestion: extract text -> dedupe by content hash ->
 * chunk -> embed -> persist. Extracted text is only ever used as inert data
 * (chunked, embedded, stored) — it is never executed or treated as an
 * instruction, so content inside an uploaded document cannot affect what
 * this service does.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentTextLoaderResolver loaderResolver;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository documentRepository;
    private final CourseChunkRepository courseChunkRepository;
    private final StudyBuddyMetrics metrics;

    public DocumentIngestionService(
            DocumentTextLoaderResolver loaderResolver,
            TextChunker textChunker,
            EmbeddingModel embeddingModel,
            DocumentRepository documentRepository,
            CourseChunkRepository courseChunkRepository,
            StudyBuddyMetrics metrics) {
        this.loaderResolver = loaderResolver;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.courseChunkRepository = courseChunkRepository;
        this.metrics = metrics;
    }

    @Transactional
    public DocumentUploadResponse ingest(MultipartFile file, String topic) {
        long startNanos = System.nanoTime();
        if (file == null || file.isEmpty()) {
            throw new DocumentProcessingException("Uploaded file is empty");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        DocumentTextLoader loader = loaderResolver.resolve(filename);
        String text = extractText(loader, file, filename);

        if (!StringUtils.hasText(text)) {
            throw new DocumentProcessingException(
                    "No extractable text found in '" + filename + "'");
        }

        String contentHash = sha256(text);

        Optional<DocumentRecord> existing = documentRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            DocumentRecord duplicate = existing.get();
            log.info("document-ingestion status=DUPLICATE documentId={}", duplicate.id());
            return new DocumentUploadResponse(
                    duplicate.id(), duplicate.sourceFilename(), duplicate.chunkCount(), IngestionStatus.DUPLICATE);
        }

        List<TextSegment> segments = textChunker.chunk(text);
        if (segments.isEmpty()) {
            throw new DocumentProcessingException(
                    "Document '" + filename + "' produced no chunks");
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();

        documentRepository.insert(new DocumentRecord(
                documentId, filename, topic, contentHash, segments.size(), IngestionStatus.INGESTED.name(), now));

        List<CourseChunkRecord> chunkRecords = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            chunkRecords.add(new CourseChunkRecord(
                    UUID.randomUUID(),
                    documentId,
                    segments.get(i).text(),
                    embeddings.get(i).vector(),
                    filename,
                    topic,
                    i,
                    now));
        }
        courseChunkRepository.insertAll(chunkRecords);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        metrics.recordIngestionDuration(elapsed);
        metrics.recordChunkCount(segments.size());
        log.info("document-ingestion status=INGESTED documentId={} chunkCount={} durationMs={}",
                documentId, segments.size(), elapsed.toMillis());

        return new DocumentUploadResponse(documentId, filename, segments.size(), IngestionStatus.INGESTED);
    }

    private String extractText(DocumentTextLoader loader, MultipartFile file, String filename) {
        try (var inputStream = file.getInputStream()) {
            return loader.extractText(inputStream);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read '" + filename + "'", e);
        }
    }

    private static String sanitizeFilename(String originalFilename) {
        String cleaned = StringUtils.hasText(originalFilename)
                ? StringUtils.cleanPath(originalFilename)
                : "unknown";
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.strip().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
