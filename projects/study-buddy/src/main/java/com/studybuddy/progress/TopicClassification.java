package com.studybuddy.progress;

public enum TopicClassification {
    WEAK,
    NOT_WEAK,

    /** Fewer attempted questions than the configured minimum — not confidently classified either way. */
    INSUFFICIENT_DATA
}
