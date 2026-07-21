package com.studybuddy.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfidenceCalculatorTest {

    @Test
    void highForVeryStrongTopMatch() {
        assertThat(ConfidenceCalculator.fromTopScore(0.9)).isEqualTo(Confidence.HIGH);
    }

    @Test
    void mediumForModerateTopMatch() {
        assertThat(ConfidenceCalculator.fromTopScore(0.75)).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void lowForWeakButAboveThresholdMatch() {
        assertThat(ConfidenceCalculator.fromTopScore(0.62)).isEqualTo(Confidence.LOW);
    }

    @Test
    void boundaryAt085IsHigh() {
        assertThat(ConfidenceCalculator.fromTopScore(0.85)).isEqualTo(Confidence.HIGH);
    }

    @Test
    void boundaryAt070IsMedium() {
        assertThat(ConfidenceCalculator.fromTopScore(0.70)).isEqualTo(Confidence.MEDIUM);
    }
}
