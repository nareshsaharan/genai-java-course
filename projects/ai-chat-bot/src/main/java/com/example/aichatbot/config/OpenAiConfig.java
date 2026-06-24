package com.example.aichatbot.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the OpenAI client bean.
 *
 * The bean is only created when mock-mode is FALSE, so students don't need
 * a real API key just to run the app for the first time.
 */
@Configuration
public class OpenAiConfig {

    @Value("${openai.api-key:}")
    private String apiKey;

    /**
     * OpenAIOkHttpClient is the standard HTTP client provided by the SDK.
     * It is only registered when app.mock-mode=false.
     */
    @Bean
    @ConditionalOnProperty(name = "app.mock-mode", havingValue = "false")
    public OpenAIClient openAIClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "app.mock-mode=false but OPENAI_API_KEY is not set. " +
                "Either export the key or set app.mock-mode=true."
            );
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
