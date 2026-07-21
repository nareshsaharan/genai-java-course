package com.studybuddy.tutor;

/** Bands the top retrieved chunk's similarity score into a confidence level. */
final class ConfidenceCalculator {

    private static final double HIGH_THRESHOLD = 0.85;
    private static final double MEDIUM_THRESHOLD = 0.70;

    private ConfidenceCalculator() {
    }

    static Confidence fromTopScore(double topScore) {
        if (topScore >= HIGH_THRESHOLD) {
            return Confidence.HIGH;
        }
        if (topScore >= MEDIUM_THRESHOLD) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }
}
