package com.example.aichatbot.cache;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ResponseCache – stores full AI answers so identical questions skip the API.
 *
 * Uses a plain HashMap: simple to read, easy to debug, no extra libraries.
 *
 * Lesson concepts: caching, HashMap, Optional.
 */
@Component
public class ResponseCache {

    // Key = user question (lowercased + trimmed), Value = full answer text
    private final Map<String, String> store = new HashMap<>();

    /** Returns the cached answer, or empty if not yet seen. */
    public Optional<String> get(String question) {
        return Optional.ofNullable(store.get(normalize(question)));
    }

    /** Saves an answer so it can be served from cache next time. */
    public void put(String question, String answer) {
        store.put(normalize(question), answer);
    }

    /** Normalise the key so "Hello" and "hello " map to the same entry. */
    private String normalize(String question) {
        return question.trim().toLowerCase();
    }
}
