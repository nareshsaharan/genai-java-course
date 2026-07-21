package com.studybuddy.tutor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices interface backing tutor chat. The prompt handed to
 * {@link #answer} already contains the retrieved course-note context plus
 * the student's question, assembled by {@link TutorChatService}.
 */
public interface TutorAssistant {

    @SystemMessage(TutorPrompts.SYSTEM_PROMPT)
    String answer(@UserMessage String prompt);
}
