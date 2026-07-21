package com.studybuddy.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Chunking and retrieval settings for the RAG pipeline: how course notes
 * are split before embedding, and how many chunks (and at what minimum
 * similarity score) are retrieved to ground a tutor chat answer.
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.rag")
public record RagProperties(

        @Positive
        int chunkSizeTokens,

        @PositiveOrZero
        int chunkOverlapTokens,

        @Positive
        int maxResults,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double minScore
) {
}
