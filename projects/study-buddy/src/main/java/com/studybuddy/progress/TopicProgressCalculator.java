package com.studybuddy.progress;

import com.studybuddy.progress.repository.TopicProgressRecord;

/**
 * Blends a new quiz attempt into a topic's accumulated progress.
 *
 * <p>{@code correctCount}/{@code totalCount} are simple cumulative sums —
 * an honest audit trail of every question ever attempted. {@code accuracy}
 * is different: it's an exponentially-weighted moving average (EWMA) so
 * that recent performance counts slightly more than a plain lifetime ratio
 * would. Each new attempt's accuracy is blended with the prior weighted
 * accuracy using {@code recencyWeight} (typically 0.4): the new attempt
 * gets that weight, and everything before it — already itself a blend of
 * everything before <em>that</em> — gets the rest. Because this recurses,
 * an attempt from N submissions ago contributes with weight roughly
 * {@code recencyWeight * (1 - recencyWeight)^N} — a smooth geometric decay,
 * not a hard cutoff.
 */
final class TopicProgressCalculator {

    private TopicProgressCalculator() {
    }

    record Blended(int correctCount, int totalCount, double accuracy) {
    }

    static Blended blend(TopicProgressRecord existing, int attemptCorrect, int attemptTotal, double recencyWeight) {
        double attemptAccuracy = (double) attemptCorrect / attemptTotal;

        if (existing == null) {
            return new Blended(attemptCorrect, attemptTotal, attemptAccuracy);
        }

        int newCorrect = existing.correctCount() + attemptCorrect;
        int newTotal = existing.totalCount() + attemptTotal;
        double newAccuracy = recencyWeight * attemptAccuracy + (1 - recencyWeight) * existing.accuracy();

        return new Blended(newCorrect, newTotal, newAccuracy);
    }
}
