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
import java.util.Scanner;

/**
 * A tiny console chat app that talks to Claude using the official Anthropic Java SDK.
 *
 * <p>How it works:
 * 1. We read your API key from the ANTHROPIC_API_KEY environment variable.
 * 2. We build an AnthropicClient using that key.
 * 3. We loop: read a line you type, send it (plus the conversation so far) to Claude,
 *    print Claude's reply, and repeat until you type "exit" or "quit".
 */
public class Main {

    // Which Claude model to use for every request in this demo.
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5;

    // Maximum number of tokens Claude is allowed to generate per reply.
    private static final long MAX_TOKENS = 1024L;

    public static void main(String[] args) {
        // Step 1: Read the API key from the environment.
        // We check it ourselves (instead of only relying on the SDK) so we can
        // print a clear, beginner-friendly error message if it's missing.
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Missing ANTHROPIC_API_KEY environment variable.");
            System.err.println("Set it before running this program, for example:");
            System.err.println("  export ANTHROPIC_API_KEY=your-api-key-here");
            System.exit(1);
            return;
        }

        // Step 2: Build the Anthropic client using our API key.
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        // We keep the full conversation history here, because the API is stateless:
        // every request must include all previous messages for Claude to "remember" them.
        List<MessageParam> conversation = new ArrayList<>();

        System.out.println("Claude console demo. Type your message and press Enter.");
        System.out.println("Type 'exit' or 'quit' to stop.");
        System.out.println();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                if (!scanner.hasNextLine()) {
                    break; // Input stream closed (e.g., Ctrl+D)
                }
                String userInput = scanner.nextLine().trim();

                if (userInput.isEmpty()) {
                    continue;
                }
                if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                // Add the user's message to the conversation history.
                conversation.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(userInput)
                        .build());

                // Step 3: Send the whole conversation so far to Claude.
                MessageCreateParams params = MessageCreateParams.builder()
                        .model(MODEL)
                        .maxTokens(MAX_TOKENS)
                        .messages(conversation)
                        .build();

                Message response = client.messages().create(params);

                // Extract and print Claude's reply. A response can contain multiple
                // content blocks, so we only look at the text ones.
                StringBuilder replyText = new StringBuilder();
                for (ContentBlock block : response.content()) {
                    block.text().ifPresent(textBlock -> replyText.append(textBlock.text()));
                }

                System.out.println("Claude: " + replyText);
                System.out.println();

                // Add Claude's reply to the conversation history too, so the next
                // request still has full context.
                conversation.add(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .content(replyText.toString())
                        .build());
            }
        }
    }
}
