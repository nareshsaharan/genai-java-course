package com.studybuddy.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

class TopicProgressCalculatorTest {

    private static final double RECENCY_WEIGHT = 0.4;

    @Test
    void firstAttemptOnANewTopicSetsAccuracyToThatAttemptsAccuracy() {
        TopicProgressCalculator.Blended result =
                TopicProgressCalculator.blend(null, 3, 4, RECENCY_WEIGHT);

        assertThat(result.correctCount()).isEqualTo(3);
        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.accuracy()).isCloseTo(0.75, offset(1e-9));
    }

    @Test
    void countsAccumulateCumulatively() {
        var existing = existingProgress(6, 10, 0.6);

        TopicProgressCalculator.Blended result =
                TopicProgressCalculator.blend(existing, 4, 5, RECENCY_WEIGHT);

        assertThat(result.correctCount()).isEqualTo(10);
        assertThat(result.totalCount()).isEqualTo(15);
    }

    @Test
    void accuracyBlendsRecentAttemptWithPriorHistoryUsingRecencyWeight() {
        // prior accuracy 0.5, new attempt accuracy 1.0 (5/5), weight 0.4
        // expected = 0.4 * 1.0 + 0.6 * 0.5 = 0.7
        var existing = existingProgress(5, 10, 0.5);

        TopicProgressCalculator.Blended result =
                TopicProgressCalculator.blend(existing, 5, 5, RECENCY_WEIGHT);

        assertThat(result.accuracy()).isCloseTo(0.7, offset(1e-9));
    }

    @Test
    void recentAttemptCountsMoreThanASingleOldAttemptButNotOverwhelmingly() {
        // A poor recent attempt should pull accuracy down, but not as far as
        // if it were the only signal — prior history still has 60% say.
        var existing = existingProgress(9, 10, 0.9);

        TopicProgressCalculator.Blended result =
                TopicProgressCalculator.blend(existing, 0, 2, RECENCY_WEIGHT);

        // new attempt accuracy = 0.0; expected = 0.4*0.0 + 0.6*0.9 = 0.54
        assertThat(result.accuracy()).isCloseTo(0.54, offset(1e-9));
        assertThat(result.accuracy()).isLessThan(existing.accuracy());
        assertThat(result.accuracy()).isGreaterThan(0.0);
    }

    private static com.studybuddy.progress.repository.TopicProgressRecord existingProgress(
            int correct, int total, double accuracy) {
        return new com.studybuddy.progress.repository.TopicProgressRecord(
                "Spring Boot", correct, total, accuracy, null, null, null);
    }
}
