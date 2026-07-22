package com.studybuddy.config;

import java.time.Duration;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.studybuddy.config.properties.ClaudeProperties;
import com.studybuddy.config.properties.GeminiProperties;
import com.studybuddy.config.properties.GroqProperties;
import com.studybuddy.config.properties.OpenRouterProperties;
import com.studybuddy.settings.ChatProvider;
import com.studybuddy.settings.RuntimeSecretsService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * {@link ChatModel} implementation that reads the currently-selected
 * {@link ChatProvider} and its API key from {@link RuntimeSecretsService} on
 * every call, instead of a provider/key baked in permanently at application
 * startup. Caches a real client keyed by (provider, key), rebuilding only
 * when either actually changes — so a normal request pays no extra cost
 * beyond the first one after a Settings change. {@code TutorAssistant} /
 * {@code FlashcardGenerator} / {@code QuizGenerator} are unaffected since
 * they only depend on the {@link ChatModel} interface.
 *
 * <p>Groq and OpenRouter are both OpenAI-compatible chat completion APIs
 * (same request/response shape, just a different base URL), so they're
 * built as a plain {@link OpenAiChatModel} with a custom {@code baseUrl}
 * rather than needing their own SDK integrations. Gemini uses LangChain4j's
 * native Google AI integration directly.
 *
 * <p>When no key is configured for the selected provider (Mock Mode — the
 * default for every new session, see {@link RuntimeSecretsService}), no real
 * API call is made at all: a canned {@link ChatResponse} is returned
 * instead, so Tutor, Flashcards, and Quiz all stay fully usable with zero
 * API keys.
 */
@Component
public class DynamicChatModel implements ChatModel {

    private final RuntimeSecretsService secrets;
    private final ClaudeProperties claudeProperties;
    private final GroqProperties groqProperties;
    private final OpenRouterProperties openRouterProperties;
    private final GeminiProperties geminiProperties;

    private volatile String cachedIdentity;
    private volatile ChatModel cachedModel;

    public DynamicChatModel(
            RuntimeSecretsService secrets,
            ClaudeProperties claudeProperties,
            GroqProperties groqProperties,
            OpenRouterProperties openRouterProperties,
            GeminiProperties geminiProperties) {
        this.secrets = secrets;
        this.claudeProperties = claudeProperties;
        this.groqProperties = groqProperties;
        this.openRouterProperties = openRouterProperties;
        this.geminiProperties = geminiProperties;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String currentKey = secrets.getActiveChatKey();
        if (currentKey == null || currentKey.isBlank()) {
            return mockResponse(chatRequest);
        }
        return resolve(secrets.getChatProvider(), currentKey).chat(chatRequest);
    }

    /** Package-visible so the test can assert on cache identity without a real network call. */
    ChatModel resolveForTest() {
        return resolve(secrets.getChatProvider(), secrets.getActiveChatKey());
    }

    private static ChatResponse mockResponse(ChatRequest chatRequest) {
        String combinedText = chatRequest.messages().stream()
                .map(DynamicChatModel::extractText)
                .collect(Collectors.joining("\n"));
        return ChatResponse.builder()
                .aiMessage(new AiMessage(MockAiResponses.forPrompt(combinedText)))
                .build();
    }

    private static String extractText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        return message.toString();
    }

    private synchronized ChatModel resolve(ChatProvider provider, String currentKey) {
        String identity = provider.name() + "|" + currentKey;
        if (!identity.equals(cachedIdentity)) {
            cachedModel = build(provider, currentKey);
            cachedIdentity = identity;
        }
        return cachedModel;
    }

    private ChatModel build(ChatProvider provider, String apiKey) {
        return switch (provider) {
            case CLAUDE -> buildClaude(apiKey);
            case GROQ -> OpenAiChatModel.builder()
                    .baseUrl(groqProperties.baseUrl())
                    .apiKey(apiKey)
                    .modelName(groqProperties.model())
                    .timeout(Duration.ofSeconds(groqProperties.timeoutSeconds()))
                    .maxRetries(2)
                    .build();
            case OPENROUTER -> OpenAiChatModel.builder()
                    .baseUrl(openRouterProperties.baseUrl())
                    .apiKey(apiKey)
                    .modelName(openRouterProperties.model())
                    .timeout(Duration.ofSeconds(openRouterProperties.timeoutSeconds()))
                    .maxRetries(2)
                    .build();
            case GEMINI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(geminiProperties.chatModel())
                    .timeout(Duration.ofSeconds(geminiProperties.timeoutSeconds()))
                    .maxRetries(2)
                    .build();
        };
    }

    private AnthropicChatModel buildClaude(String apiKey) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(claudeProperties.model())
                .maxTokens(claudeProperties.maxTokens())
                .timeout(Duration.ofSeconds(claudeProperties.timeoutSeconds()))
                .maxRetries(2);

        if (claudeProperties.temperature() != null) {
            builder.temperature(claudeProperties.temperature());
        }
        return builder.build();
    }
}
