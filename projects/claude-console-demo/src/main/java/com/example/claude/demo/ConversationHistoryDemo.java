package com.example.claude.demo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows how a Java application - not Claude itself - is what makes a
 * conversation "remember" earlier turns.
 *
 * <p>Claude does NOT remember anything automatically between API calls (see
 * {@link StatelessDemo} for a demo of that). Every request to the Messages
 * API is independent. The only reason a chat assistant appears to have
 * memory is that the CALLER resends the entire conversation so far - every
 * previous user message and every previous Claude reply - on each new
 * request. That's exactly what this class does, by hand, using a plain
 * {@code List<MessageParam>}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Add a user message: "My name is Ankur."</li>
 *   <li>Call Claude with just that message.</li>
 *   <li>Store Claude's reply back into the history list.</li>
 *   <li>Add a second user message: "What is my name?"</li>
 *   <li>Call Claude again - this time sending the FULL history (all 3
 *       messages so far), not just the new question.</li>
 *   <li>Print the final answer, which should now correctly say "Ankur",
 *       because the name is present somewhere in the history we resent.</li>
 * </ol>
 */
public class ConversationHistoryDemo {

    // Model used for both calls.
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5;

    // Max tokens Claude may generate per reply.
    private static final long MAX_TOKENS = 1024L;

    public static void main(String[] args) {
        // fromEnv() reads your API key from the ANTHROPIC_API_KEY environment
        // variable automatically.
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        // This list is OUR application's memory, not Claude's. Claude never
        // sees this list directly - we send its contents as the "messages"
        // field on every request, and Claude only ever sees what's inside
        // that one request.
        List<MessageParam> history = new ArrayList<>();

        // ── Step 1: add the first user message to our own history ──────
        history.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("My name is Ankur.")
                .build());

        // ── Step 2: call Claude with the history so far (just 1 message) ─
        System.out.println("CALL 1");
        String reply1 = callClaude(client, history);
        System.out.println("Claude: " + reply1);
        System.out.println();

        // ── Step 3: store Claude's reply back into our history ─────────
        // If we skipped this step, the next call would be missing half the
        // conversation and Claude would have no idea what it previously said.
        history.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(reply1)
                .build());

        // ── Step 4: add the second user message to our history ─────────
        history.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("What is my name?")
                .build());

        // ── Step 5: call Claude again, sending the FULL history (all 3
        // messages) - not just the new question. This is the key line that
        // makes the "conversation" work: Claude only knows the name because
        // we put "My name is Ankur." back on the wire ourselves.
        System.out.println("CALL 2 WITH HISTORY");
        String reply2 = callClaude(client, history);
        System.out.println("Claude: " + reply2);
        System.out.println();

        System.out.println("Teaching point: Claude did not remember anything by itself.");
        System.out.println("Our Java application kept a List<MessageParam> and resent");
        System.out.println("every earlier message on each new request. That resending is");
        System.out.println("the entire mechanism behind a Claude-powered chat conversation.");
    }

    /**
     * Sends the given conversation history to Claude and returns the text of
     * the reply. The caller is responsible for adding both this reply and
     * any future messages back into the history before the next call.
     */
    private static String callClaude(AnthropicClient client, List<MessageParam> history) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                // .messages(history) sends the ENTIRE conversation so far,
                // not just the latest message.
                .messages(history)
                .build();

        Message response = client.messages().create(params);

        StringBuilder reply = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> reply.append(textBlock.text()));
        }
        return reply.toString();
    }
}
