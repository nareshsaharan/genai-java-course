package com.studybuddy.settings;

/** Which provider powers Tutor/Flashcards/Quiz generation for a session. See {@link DynamicChatModel}. */
public enum ChatProvider {
    CLAUDE,
    GROQ,
    OPENROUTER,
    GEMINI
}
