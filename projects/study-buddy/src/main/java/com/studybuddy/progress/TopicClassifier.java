package com.studybuddy.progress;

/**
 * Classifies a topic's weakness from its accumulated stats. A topic is only
 * ever called {@link TopicClassification#WEAK} once it has at least
 * {@code minAttempts} attempted questions — below that, low accuracy could
 * just be noise from one unlucky question, so classification is deliberately
 * withheld rather than "strongly" declaring it weak.
 */
final class TopicClassifier {

    private TopicClassifier() {
    }

    static TopicClassification classify(int totalCount, double accuracy, int minAttempts, double weakThreshold) {
        if (totalCount < minAttempts) {
            return TopicClassification.INSUFFICIENT_DATA;
        }
        return accuracy < weakThreshold ? TopicClassification.WEAK : TopicClassification.NOT_WEAK;
    }
}
