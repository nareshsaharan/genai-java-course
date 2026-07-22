package com.studybuddy.config;

/**
 * Canned text used by {@link DynamicChatModel} when no key is configured for
 * the currently-selected chat provider (Mock Mode). LangChain4j's
 * {@code AiServices} proxies (used by {@code TutorAssistant},
 * {@code FlashcardGenerator}, {@code QuizGenerator}) parse a model's raw
 * {@code AiMessage.text()} for the first {@code {...}} JSON object when the
 * target return type isn't {@code String} — the JSON below is shaped to
 * match {@code FlashcardBatch}/{@code GeneratedQuizBatch} exactly so those
 * proxies parse it the same way they'd parse a real response.
 */
final class MockAiResponses {

    private MockAiResponses() {
    }

    static String forPrompt(String combinedPromptText) {
        if (combinedPromptText.contains("correctOptionIndex")) {
            return quiz();
        }
        if (combinedPromptText.contains("\"cards\"")) {
            return flashcards();
        }
        return tutorAnswer();
    }

    private static String tutorAnswer() {
        return "This is a demo answer from Study Buddy's Mock Mode. No API key is "
                + "configured for the selected chat provider this session, so no real AI call was "
                + "made and this text isn't grounded in your uploaded course material. Add a key "
                + "(Claude, Groq, OpenRouter, or Gemini) in the Settings tab to get real, "
                + "document-grounded answers.";
    }

    private static String flashcards() {
        return "Mock Mode is active (no chat provider key configured), so these are example "
                + "flashcards, not generated from your document: "
                + "{\"cards\":["
                + "{\"question\":\"What is Study Buddy's Mock Mode?\","
                + "\"answer\":\"A demo mode that returns example content when no API key is configured, so you can explore the app for free.\"},"
                + "{\"question\":\"How do I get flashcards generated from my own document?\","
                + "\"answer\":\"Add a Claude, Groq, OpenRouter, or Gemini API key in the Settings tab.\"},"
                + "{\"question\":\"Does Mock Mode read my uploaded document?\","
                + "\"answer\":\"No — mock responses are canned examples and don't reflect your document content.\"}"
                + "]}";
    }

    private static String quiz() {
        return "Mock Mode is active (no chat provider key configured), so this is an example quiz, "
                + "not generated from your document: "
                + "{\"questions\":["
                + "{\"question\":\"What is Study Buddy's Mock Mode?\","
                + "\"options\":[\"A demo mode with example content\",\"A billing plan\",\"A caching layer\",\"A login screen\"],"
                + "\"correctOptionIndex\":0},"
                + "{\"question\":\"How do you get real, document-grounded quiz questions?\","
                + "\"options\":[\"Wait 24 hours\",\"Add a chat provider API key in Settings\",\"Reinstall the app\",\"Nothing needed\"],"
                + "\"correctOptionIndex\":1}"
                + "]}";
    }
}
