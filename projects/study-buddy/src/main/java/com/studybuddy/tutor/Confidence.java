package com.studybuddy.tutor;

/** How well the retrieved course-note context supports the generated answer. */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW,

    /** No retrieved chunk crossed the minimum similarity threshold; Claude was not called. */
    NO_RELEVANT_CONTEXT
}
