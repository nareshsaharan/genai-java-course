package com.example.day1rag.service;

/**
 * Represents "the LLM" in our RAG pipeline: something that takes a
 * prompt (context + question) and returns a natural-language answer.
 *
 * In production this would call a real model (e.g. Claude, GPT). For
 * Day 1 we use MockLlmService instead — no API key, no network call —
 * so students can see the full RAG flow end-to-end before wiring up a
 * real model in a later lesson.
 */
public interface LlmService {

    String generateAnswer(String prompt);
}
