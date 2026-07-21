package com.studybuddy.progress;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.config.properties.ProgressProperties;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.StudyPlan;
import com.studybuddy.progress.dto.TopicProgressView;
import com.studybuddy.progress.repository.TopicProgressRecord;
import com.studybuddy.progress.repository.TopicProgressRepository;

/**
 * Owns all reads and writes of per-topic quiz performance: recording new
 * attempts (via {@link TopicProgressCalculator}), classifying topics (via
 * {@link TopicClassifier}), and picking the next topic to recommend (via
 * {@link TopicRecommender}). {@code QuizService} calls {@link #recordAttempt}
 * after scoring a submission rather than touching the repository directly.
 */
@Service
public class ProgressService {

    private final TopicProgressRepository repository;
    private final ProgressProperties properties;

    public ProgressService(TopicProgressRepository repository, ProgressProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void recordAttempt(String topic, int correctCount, int totalCount) {
        TopicProgressRecord existing = repository.findByTopic(topic).orElse(null);
        TopicProgressCalculator.Blended blended =
                TopicProgressCalculator.blend(existing, correctCount, totalCount, properties.recencyWeight());
        repository.upsert(topic, blended.correctCount(), blended.totalCount(), blended.accuracy(), Instant.now());
    }

    public List<TopicProgressView> getTopics() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    public List<TopicProgressView> getWeakTopics() {
        return getTopics().stream()
                .filter(t -> t.classification() == TopicClassification.WEAK)
                .toList();
    }

    public RecommendationResponse getRecommendation() {
        List<TopicProgressRecord> all = repository.findAll();
        if (all.isEmpty()) {
            throw new ResourceNotFoundException("No quiz attempts recorded yet — take a quiz to get a recommendation.");
        }

        List<TopicProgressRecord> weak = all.stream()
                .filter(t -> classify(t) == TopicClassification.WEAK)
                .toList();

        Optional<TopicProgressRecord> chosen = TopicRecommender.recommend(weak, properties.similarityTolerance());
        if (chosen.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No weak topics identified — keep practicing to build enough history, or great work if scores are already strong.");
        }

        String reason = TopicRecommender.buildReason(chosen.get(), weak, properties.similarityTolerance());
        repository.markRecommended(chosen.get().topic(), Instant.now());

        return new RecommendationResponse(chosen.get().topic(), reason, chosen.get().accuracy(), chosen.get().totalCount());
    }

    public Optional<TopicProgressView> getTopic(String topic) {
        return repository.findByTopic(topic).map(this::toView);
    }

    /**
     * Best-effort aggregate: weak topics plus the single next-topic
     * recommendation. Unlike {@link #getRecommendation()}, this never
     * throws for "no data yet" or "no weak topics" — those are valid study
     * states a plan can represent (as an empty topic list / null
     * recommendation) rather than failures of the whole method.
     */
    public StudyPlan generateStudyPlan() {
        List<TopicProgressView> weak = getWeakTopics();
        RecommendationResponse recommendation;
        try {
            recommendation = getRecommendation();
        } catch (ResourceNotFoundException e) {
            recommendation = null;
        }
        return new StudyPlan(weak, recommendation);
    }

    private TopicProgressView toView(TopicProgressRecord record) {
        return new TopicProgressView(
                record.topic(), record.correctCount(), record.totalCount(), record.accuracy(),
                record.lastAttemptAt(), classify(record));
    }

    private TopicClassification classify(TopicProgressRecord record) {
        return TopicClassifier.classify(
                record.totalCount(), record.accuracy(), properties.minAttemptsForClassification(), properties.weakAccuracyThreshold());
    }
}
