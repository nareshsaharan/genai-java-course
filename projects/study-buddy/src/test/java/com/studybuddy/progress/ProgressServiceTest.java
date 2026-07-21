package com.studybuddy.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.config.properties.ProgressProperties;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.StudyPlan;
import com.studybuddy.progress.dto.TopicProgressView;
import com.studybuddy.progress.repository.TopicProgressRecord;
import com.studybuddy.progress.repository.TopicProgressRepository;

class ProgressServiceTest {

    private final TopicProgressRepository repository = mock(TopicProgressRepository.class);
    private final ProgressProperties properties = new ProgressProperties(5, 0.6, 0.4, 0.05);
    private final ProgressService service = new ProgressService(repository, properties);

    private static TopicProgressRecord record(String topic, int correct, int total, double accuracy, Instant lastRecommendedAt) {
        return new TopicProgressRecord(topic, correct, total, accuracy, Instant.now(), lastRecommendedAt, Instant.now());
    }

    // ---------- recordAttempt ----------

    @Test
    void recordAttemptCreatesNewTopicProgressWhenNoneExists() {
        when(repository.findByTopic("RAG")).thenReturn(Optional.empty());

        service.recordAttempt("RAG", 3, 4);

        verify(repository).upsert(eq("RAG"), eq(3), eq(4), eq(0.75), any());
    }

    @Test
    void recordAttemptBlendsWithExistingProgress() {
        when(repository.findByTopic("RAG")).thenReturn(Optional.of(record("RAG", 8, 10, 0.8, null)));

        service.recordAttempt("RAG", 5, 5);

        // attempt accuracy = 1.0; blended = 0.4*1.0 + 0.6*0.8 = 0.88; counts 13/15
        verify(repository).upsert(eq("RAG"), eq(13), eq(15), eq(0.88), any());
    }

    // ---------- getTopics ----------

    @Test
    void getTopicsMapsClassificationForEachTopic() {
        when(repository.findAll()).thenReturn(List.of(
                record("RAG", 8, 10, 0.8, null),      // NOT_WEAK (>= threshold, enough attempts)
                record("Spring Boot", 1, 3, 0.3, null), // INSUFFICIENT_DATA (< 5 attempts)
                record("Recursion", 2, 6, 0.33, null)));  // WEAK (>= 5 attempts, below threshold)

        List<TopicProgressView> result = service.getTopics();

        assertThat(result).hasSize(3);
        assertThat(result).filteredOn(t -> t.topic().equals("RAG"))
                .extracting(TopicProgressView::classification).containsExactly(TopicClassification.NOT_WEAK);
        assertThat(result).filteredOn(t -> t.topic().equals("Spring Boot"))
                .extracting(TopicProgressView::classification).containsExactly(TopicClassification.INSUFFICIENT_DATA);
        assertThat(result).filteredOn(t -> t.topic().equals("Recursion"))
                .extracting(TopicProgressView::classification).containsExactly(TopicClassification.WEAK);
    }

    // ---------- getWeakTopics ----------

    @Test
    void getWeakTopicsReturnsOnlyWeakClassifiedTopics() {
        when(repository.findAll()).thenReturn(List.of(
                record("RAG", 8, 10, 0.8, null),
                record("Recursion", 2, 6, 0.33, null)));

        List<TopicProgressView> result = service.getWeakTopics();

        assertThat(result).extracting(TopicProgressView::topic).containsExactly("Recursion");
    }

    // ---------- getRecommendation ----------

    @Test
    void getRecommendationReturnsWeakestTopicWithReasonAndMarksItRecommended() {
        when(repository.findAll()).thenReturn(List.of(
                record("RAG", 8, 10, 0.8, null),
                record("Recursion", 1, 6, 0.2, null)));

        RecommendationResponse response = service.getRecommendation();

        assertThat(response.topic()).isEqualTo("Recursion");
        assertThat(response.reason()).isNotBlank();
        assertThat(response.accuracy()).isEqualTo(0.2);
        verify(repository).markRecommended(eq("Recursion"), any());
    }

    @Test
    void getRecommendationThrowsWhenNoTopicsAtAll() {
        when(repository.findAll()).thenReturn(List.of());

        assertThatThrownBy(service::getRecommendation).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).markRecommended(any(), any());
    }

    @Test
    void getRecommendationThrowsWhenNoWeakTopics() {
        when(repository.findAll()).thenReturn(List.of(record("RAG", 9, 10, 0.9, null)));

        assertThatThrownBy(service::getRecommendation).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).markRecommended(any(), any());
    }

    @Test
    void getRecommendationRotatesAwayFromRecentlyRecommendedNearTiedTopic() {
        Instant now = Instant.now();
        when(repository.findAll()).thenReturn(List.of(
                record("Spring Boot", 2, 6, 0.40, now.minus(1, ChronoUnit.HOURS)),
                record("Recursion", 2, 6, 0.42, null)));

        RecommendationResponse response = service.getRecommendation();

        assertThat(response.topic()).isEqualTo("Recursion");
    }

    // ---------- getTopic ----------

    @Test
    void getTopicReturnsMappedViewWhenTopicExists() {
        when(repository.findByTopic("RAG")).thenReturn(Optional.of(record("RAG", 8, 10, 0.8, null)));

        Optional<TopicProgressView> result = service.getTopic("RAG");

        assertThat(result).isPresent();
        assertThat(result.get().topic()).isEqualTo("RAG");
        assertThat(result.get().classification()).isEqualTo(TopicClassification.NOT_WEAK);
    }

    @Test
    void getTopicReturnsEmptyWhenTopicHasNoData() {
        when(repository.findByTopic("Quantum Computing")).thenReturn(Optional.empty());

        assertThat(service.getTopic("Quantum Computing")).isEmpty();
    }

    // ---------- generateStudyPlan ----------

    @Test
    void generateStudyPlanIncludesWeakTopicsAndRecommendationWhenBothExist() {
        when(repository.findAll()).thenReturn(List.of(
                record("RAG", 8, 10, 0.8, null),
                record("Recursion", 1, 6, 0.2, null)));

        StudyPlan plan = service.generateStudyPlan();

        assertThat(plan.weakTopics()).extracting(TopicProgressView::topic).containsExactly("Recursion");
        assertThat(plan.recommendation()).isNotNull();
        assertThat(plan.recommendation().topic()).isEqualTo("Recursion");
    }

    @Test
    void generateStudyPlanHasNullRecommendationWhenNoWeakTopicsExist() {
        when(repository.findAll()).thenReturn(List.of(record("RAG", 9, 10, 0.9, null)));

        StudyPlan plan = service.generateStudyPlan();

        assertThat(plan.weakTopics()).isEmpty();
        assertThat(plan.recommendation()).isNull();
    }

    @Test
    void generateStudyPlanHasEmptyWeakTopicsAndNullRecommendationWhenNoDataAtAll() {
        when(repository.findAll()).thenReturn(List.of());

        StudyPlan plan = service.generateStudyPlan();

        assertThat(plan.weakTopics()).isEmpty();
        assertThat(plan.recommendation()).isNull();
    }
}
