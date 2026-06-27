package com.example.aichatbot.store;

import com.example.aichatbot.model.ConversationMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemoryConversationStore stores chat history inside the application's memory.
 *
 * How it works:
 * We use a ConcurrentHashMap where:
 *   key   = conversationId  (e.g. "user-abc-123")
 *   value = list of recent messages for that conversation
 *
 * Why ConcurrentHashMap?
 * Multiple HTTP requests can arrive at the same time (one per user).
 * A regular HashMap is not thread-safe — two threads writing simultaneously
 * can corrupt the data. ConcurrentHashMap handles this safely.
 *
 * Why only 10 messages?
 * AI models charge per token (word/piece of text). Sending the full history
 * of a long conversation gets expensive fast. Keeping the last 10 messages
 * gives the model enough context without blowing up costs.
 *
 * Session isolation:
 * Each conversationId has its own list. User A's messages never appear in
 * User B's list — even though both lists live in the same map.
 *
 * Limitation:
 * This store lives only as long as the application is running. A server
 * restart wipes all conversations. A future Redis-backed store would fix that.
 */
@Component
public class InMemoryConversationStore implements ConversationStore {

    private static final int MAX_MESSAGES = 10;

    // The map that holds every active conversation in memory.
    private final ConcurrentHashMap<String, List<ConversationMessage>> store = new ConcurrentHashMap<>();

    @Override
    public void addMessage(String conversationId, ConversationMessage message) {
        // computeIfAbsent creates a fresh list the first time we see a conversationId.
        List<ConversationMessage> messages = store.computeIfAbsent(conversationId, id -> new ArrayList<>());

        synchronized (messages) {
            messages.add(message);

            // Trim to the last MAX_MESSAGES so the list never grows unbounded.
            if (messages.size() > MAX_MESSAGES) {
                messages.subList(0, messages.size() - MAX_MESSAGES).clear();
            }
        }
    }

    @Override
    public List<ConversationMessage> getMessages(String conversationId) {
        List<ConversationMessage> messages = store.get(conversationId);
        if (messages == null) {
            return Collections.emptyList();
        }
        synchronized (messages) {
            return new ArrayList<>(messages); // return a copy so callers can't modify our internal list
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        store.remove(conversationId);
    }
}
