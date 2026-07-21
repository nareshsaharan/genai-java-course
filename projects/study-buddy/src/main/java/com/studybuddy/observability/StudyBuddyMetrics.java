package com.studybuddy.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Single place where every custom Study Buddy metric is named and recorded,
 * so metric names/tags stay consistent across ingestion, tutor chat and
 * flashcard generation rather than being scattered ad hoc through services.
 */
@Component
public class StudyBuddyMetrics {

    private final MeterRegistry registry;

    public StudyBuddyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Wall-clock time to fully ingest a (non-duplicate) document: extract + chunk + embed + store. */
    public void recordIngestionDuration(Duration duration) {
        registry.timer("studybuddy.ingestion.duration").record(duration);
    }

    /** Number of chunks produced by one ingestion. */
    public void recordChunkCount(int chunkCount) {
        registry.summary("studybuddy.ingestion.chunks").record(chunkCount);
    }

    /** pgvector similarity-search latency, tagged by which feature triggered it ("tutor" or "flashcard"). */
    public void recordRetrievalLatency(String feature, Duration duration) {
        registry.timer("studybuddy.retrieval.latency", "feature", feature).record(duration);
    }

    /** Claude call latency, tagged by feature. */
    public void recordClaudeLatency(String feature, Duration duration) {
        registry.timer("studybuddy.claude.latency", "feature", feature).record(duration);
    }

    /** Incremented every time retrieval finds nothing above the similarity floor, so Claude is skipped. */
    public void incrementNoContext(String feature) {
        registry.counter("studybuddy.no_context.count", "feature", feature).increment();
    }

    /** Incremented every time a Claude call fails or times out. */
    public void incrementModelFailure(String feature) {
        registry.counter("studybuddy.model.failure.count", "feature", feature).increment();
    }
}
