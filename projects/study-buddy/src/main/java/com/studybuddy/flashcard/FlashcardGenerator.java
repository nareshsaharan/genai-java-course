package com.studybuddy.flashcard;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices interface for flashcard generation. The return
 * type is a typed {@link FlashcardBatch} (a single POJO wrapping the card
 * list) rather than String — or a bare {@code List<GeneratedFlashcard>}.
 * LangChain4j builds a JSON schema from the return type and, for a
 * model/provider that doesn't advertise native JSON-schema response mode
 * (Anthropic, in this LangChain4j version), transparently falls back to
 * forced tool-calling to obtain and parse that structured output. This only
 * works for a single top-level POJO: a bare {@code List<T>} return throws
 * {@code IllegalStateException} from LangChain4j's own
 * {@code PojoCollectionOutputParser} for such models — see the project
 * README for details. Either way, this interface never sees or produces
 * raw text that needs manual/regex parsing.
 */
public interface FlashcardGenerator {

    @SystemMessage(FlashcardPrompts.SYSTEM_PROMPT)
    FlashcardBatch generate(@UserMessage String prompt);
}
