package com.studybuddy.flashcard;

import java.util.List;

/**
 * Wraps the generated cards in a single top-level object. LangChain4j's
 * AiServices only implements real (tool-call-based) structured-output
 * support for a single-POJO return type — a bare {@code List<T>} top-level
 * return throws {@code IllegalStateException} from
 * {@code PojoCollectionOutputParser.formatInstructions()} for any model
 * that doesn't advertise native JSON-schema response support (Anthropic, in
 * this LangChain4j version). See the README's "typed structured output"
 * section.
 */
public record FlashcardBatch(List<GeneratedFlashcard> cards) {
}
