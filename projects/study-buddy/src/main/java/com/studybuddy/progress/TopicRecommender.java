package com.studybuddy.progress;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.studybuddy.progress.repository.TopicProgressRecord;

/**
 * Picks the next topic to study out of a set of weak topics.
 *
 * <p>Naively always recommending the single lowest-accuracy topic means a
 * student who takes the recommendation, improves slightly, but is still
 * marginally behind a close second topic gets stuck being told the same
 * thing forever. Instead: find every topic within {@code tolerance} of the
 * minimum accuracy (a "near tie"), then within that group pick whichever
 * one was recommended longest ago (or never) — rotating attention across
 * close competitors instead of fixating on one.
 */
final class TopicRecommender {

    private TopicRecommender() {
    }

    static Optional<TopicProgressRecord> recommend(List<TopicProgressRecord> weakTopics, double tolerance) {
        if (weakTopics.isEmpty()) {
            return Optional.empty();
        }

        double minAccuracy = weakTopics.stream()
                .mapToDouble(TopicProgressRecord::accuracy)
                .min()
                .orElseThrow();

        List<TopicProgressRecord> nearTied = weakTopics.stream()
                .filter(t -> t.accuracy() <= minAccuracy + tolerance)
                .toList();

        return nearTied.stream()
                .min(Comparator.comparing(
                        TopicProgressRecord::lastRecommendedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** Human-readable explanation for why a topic was chosen, for the API response. */
    static String buildReason(TopicProgressRecord chosen, List<TopicProgressRecord> weakTopics, double tolerance) {
        double minAccuracy = weakTopics.stream().mapToDouble(TopicProgressRecord::accuracy).min().orElseThrow();
        long tiedCount = weakTopics.stream().filter(t -> t.accuracy() <= minAccuracy + tolerance).count();

        String accuracyPct = String.format("%.0f%%", chosen.accuracy() * 100);
        if (tiedCount <= 1) {
            return "Lowest accuracy among weak topics (" + accuracyPct + " over " + chosen.totalCount() + " questions).";
        }
        if (chosen.lastRecommendedAt() == null) {
            return "Tied with " + (tiedCount - 1) + " other weak topic(s) near " + accuracyPct
                    + " accuracy; chosen because it hasn't been recommended before.";
        }
        return "Tied with " + (tiedCount - 1) + " other weak topic(s) near " + accuracyPct
                + " accuracy; chosen because it was recommended least recently.";
    }
}
