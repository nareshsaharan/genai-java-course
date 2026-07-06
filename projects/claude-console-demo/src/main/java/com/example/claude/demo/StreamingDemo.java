package com.example.claude.demo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;

/**
 * Shows how to use Claude's STREAMING API from Java, instead of waiting for
 * the whole answer to be ready.
 *
 * <p>Non-streaming calls (like {@link FirstClaudeCall}) send one request and
 * wait for Claude to finish the ENTIRE response before you get anything
 * back. For a long answer, that can feel slow - the user just stares at a
 * blank screen until everything is done.
 *
 * <p>Streaming instead sends the response back in small pieces ("deltas") as
 * Claude generates them, over the same connection. Your code can print each
 * piece the moment it arrives, so text appears gradually - the same
 * "typing" effect you see in claude.ai or ChatGPT. This does not make
 * Claude answer faster overall, but it makes the app FEEL faster and more
 * responsive, because the user sees progress immediately instead of
 * waiting in silence.
 */
public class StreamingDemo {

    // Model used for this demo.
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5;

    // Max tokens Claude may generate for this reply.
    private static final long MAX_TOKENS = 1024L;

    private static final String PROMPT =
            "Write a beginner-friendly explanation of Spring Boot REST Controller with example.";

    public static void main(String[] args) {
        // fromEnv() reads your API key from the ANTHROPIC_API_KEY environment
        // variable automatically.
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        // Building the request looks exactly the same as a normal (non-streaming)
        // call - model, maxTokens, and the user's message.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .addUserMessage(PROMPT)
                .build();

        System.out.println("Prompt: " + PROMPT);
        System.out.println();
        System.out.print("Claude: ");
        System.out.flush();

        // client.messages().createStreaming(...) - instead of create(...) -
        // opens a stream of events rather than returning one final Message.
        // StreamResponse implements AutoCloseable, so we use try-with-resources
        // to make sure the underlying HTTP connection is always closed, even
        // if something goes wrong while we're reading from it.
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {

            stream.stream()
                    // The stream contains several kinds of events (message
                    // started, content block started, content block delta,
                    // message finished, ...). We only care about
                    // "content block delta" events, which carry a new chunk
                    // of text.
                    .flatMap(event -> event.contentBlockDelta().stream())
                    // Within a content block delta, the actual new text is
                    // in delta().text() - other delta types exist (e.g. for
                    // thinking or tool input), so we only keep text deltas.
                    .flatMap(deltaEvent -> deltaEvent.delta().text().stream())
                    .forEach(textDelta -> {
                        // Print this chunk immediately, with no line break,
                        // so the words appear one after another.
                        System.out.print(textDelta.text());
                        // flush() pushes the chunk to the terminal right away
                        // instead of letting System.out buffer it - without
                        // this, students might see the output appear in a
                        // few big bursts instead of a smooth typing effect.
                        System.out.flush();
                    });

        } catch (AnthropicServiceException e) {
            // The SDK throws this for errors returned by the Claude API itself
            // (bad request, rate limit, authentication problem, etc). We catch
            // it separately so we can show a clear, specific message instead
            // of letting a raw stack trace scare a beginner.
            System.out.println();
            System.err.println("Claude API returned an error: " + e.getMessage());
            e.errorType().ifPresent(errorType -> System.err.println("Error type: " + errorType));
            return;
        } catch (Exception e) {
            // Anything else (e.g. a network problem) lands here.
            System.out.println();
            System.err.println("Unexpected error while streaming from Claude: " + e.getMessage());
            return;
        }

        System.out.println();
        System.out.println();
        System.out.println("Done - the text above was printed chunk by chunk as it streamed in.");
    }
}
