package com.studybuddy.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicClassifierTest {

    private static final int MIN_ATTEMPTS = 5;
    private static final double WEAK_THRESHOLD = 0.6;

    @Test
    void belowMinAttemptsIsInsufficientDataEvenWithZeroAccuracy() {
        TopicClassification result = TopicClassifier.classify(3, 0.0, MIN_ATTEMPTS, WEAK_THRESHOLD);

        assertThat(result).isEqualTo(TopicClassification.INSUFFICIENT_DATA);
    }

    @Test
    void atOrAboveMinAttemptsWithLowAccuracyIsWeak() {
        TopicClassification result = TopicClassifier.classify(5, 0.4, MIN_ATTEMPTS, WEAK_THRESHOLD);

        assertThat(result).isEqualTo(TopicClassification.WEAK);
    }

    @Test
    void atOrAboveMinAttemptsWithHighAccuracyIsNotWeak() {
        TopicClassification result = TopicClassifier.classify(10, 0.85, MIN_ATTEMPTS, WEAK_THRESHOLD);

        assertThat(result).isEqualTo(TopicClassification.NOT_WEAK);
    }

    @Test
    void accuracyExactlyAtThresholdIsNotWeak() {
        TopicClassification result = TopicClassifier.classify(10, WEAK_THRESHOLD, MIN_ATTEMPTS, WEAK_THRESHOLD);

        assertThat(result).isEqualTo(TopicClassification.NOT_WEAK);
    }
}
