package com.studybuddy.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.studybuddy.progress.repository.TopicProgressRecord;

class TopicRecommenderTest {

    private static final double TOLERANCE = 0.05;

    private static TopicProgressRecord topic(String name, double accuracy, Instant lastRecommendedAt) {
        return new TopicProgressRecord(name, 3, 10, accuracy, Instant.now(), lastRecommendedAt, Instant.now());
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(TopicRecommender.recommend(List.of(), TOLERANCE)).isEmpty();
    }

    @Test
    void singleWeakTopicIsRecommended() {
        TopicProgressRecord only = topic("Spring Boot", 0.4, null);

        Optional<TopicProgressRecord> result = TopicRecommender.recommend(List.of(only), TOLERANCE);

        assertThat(result).contains(only);
    }

    @Test
    void clearlyWeakestTopicIsRecommendedWhenNoTie() {
        TopicProgressRecord weakest = topic("Recursion", 0.2, null);
        TopicProgressRecord lessWeak = topic("Spring Boot", 0.55, null);

        Optional<TopicProgressRecord> result = TopicRecommender.recommend(List.of(lessWeak, weakest), TOLERANCE);

        assertThat(result).contains(weakest);
    }

    @Test
    void amongNearTiedTopicsThePreviouslyRecommendedOneIsNotPickedAgain() {
        Instant now = Instant.now();
        TopicProgressRecord recentlyRecommended = topic("Spring Boot", 0.40, now.minus(1, ChronoUnit.HOURS));
        TopicProgressRecord neverRecommended = topic("Recursion", 0.42, null);

        Optional<TopicProgressRecord> result = TopicRecommender.recommend(
                List.of(recentlyRecommended, neverRecommended), TOLERANCE);

        assertThat(result).contains(neverRecommended);
    }

    @Test
    void amongNearTiedTopicsTheLeastRecentlyRecommendedIsPicked() {
        Instant now = Instant.now();
        TopicProgressRecord recommendedYesterday = topic("Spring Boot", 0.40, now.minus(1, ChronoUnit.DAYS));
        TopicProgressRecord recommendedJustNow = topic("Recursion", 0.41, now.minus(1, ChronoUnit.MINUTES));

        Optional<TopicProgressRecord> result = TopicRecommender.recommend(
                List.of(recommendedJustNow, recommendedYesterday), TOLERANCE);

        assertThat(result).contains(recommendedYesterday);
    }

    @Test
    void topicOutsideToleranceBandIsNotConsideredForRotationEvenIfNeverRecommended() {
        // "Recursion" is way weaker than "Spring Boot" — not a near-tie, so the
        // recommender should not switch to Spring Boot just because Recursion
        // was recommended more recently.
        Instant now = Instant.now();
        TopicProgressRecord muchWeaker = topic("Recursion", 0.10, now.minus(1, ChronoUnit.MINUTES));
        TopicProgressRecord onlySlightlyWeak = topic("Spring Boot", 0.55, null);

        Optional<TopicProgressRecord> result = TopicRecommender.recommend(
                List.of(onlySlightlyWeak, muchWeaker), TOLERANCE);

        assertThat(result).contains(muchWeaker);
    }
}
