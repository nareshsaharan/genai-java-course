package com.studybuddy.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class StudyBuddyMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StudyBuddyMetrics metrics = new StudyBuddyMetrics(registry);

    @Test
    void recordsIngestionDuration() {
        metrics.recordIngestionDuration(Duration.ofMillis(250));

        assertThat(registry.get("studybuddy.ingestion.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("studybuddy.ingestion.duration").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isCloseTo(250.0, org.assertj.core.data.Offset.offset(5.0));
    }

    @Test
    void recordsChunkCount() {
        metrics.recordChunkCount(7);

        assertThat(registry.get("studybuddy.ingestion.chunks").summary().count()).isEqualTo(1);
        assertThat(registry.get("studybuddy.ingestion.chunks").summary().totalAmount()).isEqualTo(7.0);
    }

    @Test
    void recordsRetrievalLatencyTaggedByFeature() {
        metrics.recordRetrievalLatency("tutor", Duration.ofMillis(40));

        assertThat(registry.get("studybuddy.retrieval.latency").tag("feature", "tutor").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsClaudeLatencyTaggedByFeature() {
        metrics.recordClaudeLatency("flashcard", Duration.ofMillis(900));

        assertThat(registry.get("studybuddy.claude.latency").tag("feature", "flashcard").timer().count())
                .isEqualTo(1);
    }

    @Test
    void incrementsNoContextCounterTaggedByFeature() {
        metrics.incrementNoContext("tutor");
        metrics.incrementNoContext("tutor");

        assertThat(registry.get("studybuddy.no_context.count").tag("feature", "tutor").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    void incrementsModelFailureCounterTaggedByFeature() {
        metrics.incrementModelFailure("flashcard");

        assertThat(registry.get("studybuddy.model.failure.count").tag("feature", "flashcard").counter().count())
                .isEqualTo(1.0);
    }
}
