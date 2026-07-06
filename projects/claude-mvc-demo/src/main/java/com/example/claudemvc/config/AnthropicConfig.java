package com.example.claudemvc.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a single, shared {@link AnthropicClient} bean for the whole application.
 *
 * <p>{@code AnthropicOkHttpClient.fromEnv()} reads your API key from the
 * ANTHROPIC_API_KEY environment variable - it must be set before starting
 * this application (see the project README).
 */
@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}
