package com.studybuddy.tutor;

/** How well the retrieved course-note context supports the generated answer. */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW,

    /**
     * No retrieved chunk crossed the minimum similarity threshold. The chat
     * provider is still called, but answers from its own general knowledge
     * instead of the student's course notes — see
     * {@code TutorAssistant#answerFromGeneralKnowledge}.
     */
    NO_RELEVANT_CONTEXT
}
