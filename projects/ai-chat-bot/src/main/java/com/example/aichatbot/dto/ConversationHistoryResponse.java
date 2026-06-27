package com.example.aichatbot.dto;

import com.example.aichatbot.model.ConversationMessage;

import java.util.List;

public record ConversationHistoryResponse(String conversationId, List<ConversationMessage> messages) {}
