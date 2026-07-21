package com.studybuddy.quiz;

import java.util.List;

/**
 * Wraps the generated questions in a single top-level object. As with
 * flashcards, this must be a single POJO, not a bare {@code List<T>} — see
 * README "typed structured output" for why a bare list throws
 * {@code IllegalStateException} for models without native JSON-schema
 * response support.
 */
public record GeneratedQuizBatch(List<GeneratedQuizQuestion> questions) {
}
