package com.example.day1rag.service;

import org.springframework.stereotype.Service;

/**
 * A FAKE "LLM" so students can see the shape of RAG answer generation
 * without calling any real AI model or needing an API key.
 *
 * IMPORTANT: this is NOT what production systems use. A real LLM reads
 * the whole prompt and actually understands the question and context,
 * then writes a genuinely new answer in natural language. This mock
 * does nothing that clever — it just pulls the context section back
 * out of the prompt and hands some of it back, so we can see the whole
 * pipeline (retrieve -> build prompt -> "ask the LLM" -> answer)
 * working before swapping in a real model.
 */
@Service
public class MockLlmService implements LlmService {

    private static final String NO_ANSWER_MESSAGE = "I don't know from the provided documents.";
    private static final int MAX_ANSWER_LENGTH = 300;

    @Override
    public String generateAnswer(String prompt) {
        String context = extractContext(prompt);

        if (context.isBlank()) {
            // No context was retrieved, so there's nothing to answer from.
            return NO_ANSWER_MESSAGE;
        }

        // A real LLM would read the question and write a real answer.
        // We just hand back (a trimmed slice of) the retrieved context,
        // so students can see that the "answer" really did come from
        // the documents, not from anywhere else.
        String snippet = context.strip();
        if (snippet.length() > MAX_ANSWER_LENGTH) {
            snippet = snippet.substring(0, MAX_ANSWER_LENGTH) + "...";
        }

        return "Based on the provided course notes, " + snippet;
    }

    /**
     * Pulls the text between "Context:" and "Question:" back out of the
     * prompt built by RagService. This only works because we know the
     * exact shape of our own prompt template — a real LLM wouldn't need
     * this, it just reads the whole prompt.
     */
    private String extractContext(String prompt) {
        int contextStart = prompt.indexOf("Context:");
        int questionStart = prompt.indexOf("Question:");

        if (contextStart == -1 || questionStart == -1 || questionStart <= contextStart) {
            return "";
        }

        return prompt.substring(contextStart + "Context:".length(), questionStart);
    }
}
