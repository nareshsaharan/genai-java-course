package com.example.claude.demo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

/**
 * Demonstrates that the Claude Messages API is STATELESS.
 *
 * <p>Claude does not remember anything between separate API calls. There is
 * no server-side session or memory - if you want Claude to "remember" what
 * was said earlier, YOU must resend the earlier messages yourself as part of
 * the {@code messages} list on every request (that's what {@link Main} does
 * with its {@code conversation} list, and what {@link ConversationHistoryDemo}
 * demonstrates explicitly).
 *
 * <p>This demo proves the point by making two completely independent calls:
 * <ol>
 *   <li>CALL 1: tell Claude "My name is Ankur."</li>
 *   <li>CALL 2 WITHOUT HISTORY: ask "What is my name?" - but we do NOT include
 *       the first message in this request.</li>
 * </ol>
 * Because call 2 has no memory of call 1, Claude has no way to know the name.
 */
public class StatelessDemo {

    // Model used for both calls.
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5;

    // Max tokens Claude may generate per reply.
    private static final long MAX_TOKENS = 1024L;

    public static void main(String[] args) {
        // fromEnv() reads your API key from the ANTHROPIC_API_KEY environment
        // variable automatically.
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        // ── CALL 1: tell Claude our name ────────────────────────────────
        // This is a brand new request with a single user message. Nothing
        // about this call is stored on Claude's side after the response
        // comes back - the API itself keeps no memory of it.
        System.out.println("CALL 1");
        String reply1 = askClaude(client, "My name is Ankur.");
        System.out.println("Claude: " + reply1);
        System.out.println();

        // ── CALL 2 WITHOUT HISTORY: ask for the name back ───────────────
        // This is a completely separate, independent request. We deliberately
        // do NOT include the "My name is Ankur." message from call 1 here.
        // Because the API is stateless, Claude has no memory of call 1 and
        // therefore no way to know the answer - it can only guess, say it
        // doesn't know, or ask us to tell it.
        System.out.println("CALL 2 WITHOUT HISTORY");
        String reply2 = askClaude(client, "What is my name?");
        System.out.println("Claude: " + reply2);
        System.out.println();

        System.out.println("Teaching point: Claude could not reliably answer in CALL 2");
        System.out.println("because every API request is independent - there is no");
        System.out.println("server-side memory. To keep context across turns, you must");
        System.out.println("resend prior messages yourself (see Main.java or ConversationHistoryDemo.java).");
    }

    /**
     * Sends a single, standalone user message to Claude and returns the text
     * of the reply. Each call to this method is its own independent request -
     * no conversation history is passed in or kept between calls.
     */
    private static String askClaude(AnthropicClient client, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .addUserMessage(userMessage)
                .build();

        Message response = client.messages().create(params);

        StringBuilder reply = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> reply.append(textBlock.text()));
        }
        return reply.toString();
    }
}
