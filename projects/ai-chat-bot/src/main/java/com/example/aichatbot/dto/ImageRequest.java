package com.example.aichatbot.dto;

/**
 * quality is optional — if the caller omits it, ImageService defaults to "medium".
 * Accepted values: low, medium, high, hd, standard, auto
 */
public record ImageRequest(String prompt, String quality) {}
