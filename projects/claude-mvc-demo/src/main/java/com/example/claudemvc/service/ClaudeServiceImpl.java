package com.example.claudemvc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service layer implementation: this is the only class that calls the
 * Anthropic SDK. The controller layer depends only on the
 * {@link ClaudeService} interface, not on this implementation.
 */
@Service
public class ClaudeServiceImpl implements ClaudeService {

    // Model used for every request. Haiku is fast and inexpensive - good for a demo.
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5;

    // Maximum number of tokens Claude may generate per reply.
    private static final long MAX_TOKENS = 1024L;

    private final AnthropicClient anthropicClient;

    // Spring injects the AnthropicClient bean defined in AnthropicConfig.
    public ClaudeServiceImpl(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    @Override
    public String askClaude(String userMessage) {
        // A single standalone message is really just a one-message history.
        List<MessageParam> history = new ArrayList<>();
        history.add(userMessageParam(userMessage));
        return sendToClaude(history);
    }

    @Override
    public ConversationResult continueConversation(String firstMessage, String secondMessage) {
        // This list is OUR application's memory, not Claude's. Claude never
        // sees this list directly - we send its contents as the "messages"
        // field on every request, and Claude only ever sees what is inside
        // that one request.
        List<MessageParam> history = new ArrayList<>();

        // Step 1: add the first user message and call Claude with just that.
        history.add(userMessageParam(firstMessage));
        String firstReply = sendToClaude(history);

        // Step 2: store Claude's reply back into our history. If we skipped
        // this step, the next call would be missing half the conversation
        // and Claude would have no idea what it previously said.
        history.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(firstReply)
                .build());

        // Step 3: add the second user message, then call Claude again with
        // the FULL history (all 3 messages) - not just the new question.
        // This is the line that makes the "conversation" work: Claude only
        // has access to the first message because we resend it ourselves.
        history.add(userMessageParam(secondMessage));
        String secondReply = sendToClaude(history);

        return new ConversationResult(firstReply, secondReply);
    }

    @Override
    public void streamClaude(String userMessage, Consumer<String> onTextChunk) {
        // Same request shape as a normal call - model, maxTokens, and the
        // user's message. The only difference is which method we call below.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .addUserMessage(userMessage)
                .build();

        // client.messages().createStreaming(...) - instead of create(...) -
        // opens a stream of events rather than returning one final Message.
        // StreamResponse implements AutoCloseable, so we use try-with-resources
        // to make sure the underlying HTTP connection is always closed, even
        // if something goes wrong while we're reading from it.
        try (StreamResponse<RawMessageStreamEvent> stream = anthropicClient.messages().createStreaming(params)) {
            stream.stream()
                    // The stream carries several kinds of events (message
                    // started, content block started, content block delta,
                    // message finished, ...). We only care about
                    // "content block delta" events, which carry a new chunk
                    // of text.
                    .flatMap(event -> event.contentBlockDelta().stream())
                    // Within a content block delta, the actual new text is
                    // in delta().text() - other delta types exist (e.g. for
                    // thinking or tool input), so we only keep text deltas.
                    .flatMap(deltaEvent -> deltaEvent.delta().text().stream())
                    // Hand each chunk to the caller immediately, as soon as
                    // it arrives - this is what lets a client render text
                    // progressively instead of waiting for the full reply.
                    .forEach(textDelta -> onTextChunk.accept(textDelta.text()));
        }
    }

    private MessageParam userMessageParam(String text) {
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(text)
                .build();
    }

    /**
     * Sends the given conversation history to Claude and returns the text of
     * the reply. The caller is responsible for adding both this reply and any
     * future messages back into the history before the next call.
     */
    private String sendToClaude(List<MessageParam> history) {
        // Build the request: which model to use, how long the reply may be,
        // and the full conversation so far (not just the newest message).
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .messages(history)
                .build();

        // Send the request and wait for Claude's response.
        Message response = anthropicClient.messages().create(params);

        // A response is a list of content blocks; we only care about the
        // text ones, so we collect and concatenate them.
        StringBuilder reply = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> reply.append(textBlock.text()));
        }
        return reply.toString();
    }
}
