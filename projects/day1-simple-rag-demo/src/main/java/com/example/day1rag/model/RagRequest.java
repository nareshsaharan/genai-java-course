package com.example.day1rag.model;

/**
 * The JSON body a client sends to POST /api/rag/ask.
 *
 * Example JSON:
 * {
 *   "question": "What is RAG?",
 *   "topK": 3
 * }
 */
public class RagRequest {

    private String question;
    private int topK;

    // Spring needs a no-args constructor to build this object from JSON.
    public RagRequest() {
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
