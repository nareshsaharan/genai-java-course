package com.example.aichatbot.model;

import java.time.Instant;

public record ConversationMessage(String role, String content, Instant timestamp) {}
