package com.example.aichatbot.store;

import com.example.aichatbot.model.ConversationMessage;

import java.util.List;

/**
 * ConversationStore defines how we save and retrieve chat history.
 *
 * Why do we need this?
 * An AI model is stateless — it forgets everything after each response.
 * To have a real back-and-forth conversation, WE must remember the history
 * and send it along with every new message.
 *
 * What is a conversationId?
 * Think of it like a table number at a restaurant. Every user gets their own
 * table (conversationId), so messages from one user never mix with another's.
 * This is called "session isolation".
 *
 * Interface vs implementation:
 * We define the contract here (what to do) and leave the "how" to
 * InMemoryConversationStore — or later, a Redis-backed store.
 */
public interface ConversationStore {

    /**
     * Save a single message to a conversation's history.
     *
     * @param conversationId unique identifier for this chat session
     * @param message        the message to append (role = "user" or "assistant")
     */
    void addMessage(String conversationId, ConversationMessage message);

    /**
     * Retrieve the recent message history for a conversation.
     *
     * @param conversationId unique identifier for this chat session
     * @return ordered list of messages, oldest first
     */
    List<ConversationMessage> getMessages(String conversationId);

    /**
     * Wipe all messages for a conversation (e.g., when the user starts over).
     *
     * @param conversationId unique identifier for this chat session
     */
    void clearConversation(String conversationId);
}
