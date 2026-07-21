package com.studybuddy.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.studybuddy.config.properties.ClaudeProperties;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Single shared Claude chat model bean, reused by tutor chat and (later)
 * flashcard/quiz generation. apiKey comes only from {@link ClaudeProperties},
 * which is itself sourced from the ANTHROPIC_API_KEY environment variable —
 * never hardcoded, never logged.
 */
@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatModel(ClaudeProperties claudeProperties) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(claudeProperties.apiKey())
                .modelName(claudeProperties.model())
                .maxTokens(claudeProperties.maxTokens())
                .timeout(Duration.ofSeconds(claudeProperties.timeoutSeconds()))
                // Explicit rather than relying on LangChain4j's built-in default
                // (also 2) so the retry behavior behind TutorAnswerTimeoutException /
                // FlashcardGenerationTimeoutException / QuizGenerationTimeoutException
                // is visible here rather than implicit.
                .maxRetries(2);

        // Some Claude models reject the request outright if `temperature` is
        // sent at all (see ClaudeProperties javadoc) — only include it when
        // explicitly configured.
        if (claudeProperties.temperature() != null) {
            builder.temperature(claudeProperties.temperature());
        }

        return builder.build();
    }
}
