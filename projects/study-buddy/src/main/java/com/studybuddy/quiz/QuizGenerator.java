package com.studybuddy.quiz;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** LangChain4j AiServices interface for quiz question generation. */
public interface QuizGenerator {

    @SystemMessage(QuizPrompts.SYSTEM_PROMPT)
    GeneratedQuizBatch generate(@UserMessage String prompt);
}
