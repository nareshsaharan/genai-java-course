package com.example.claude.demo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

/**
 * The simplest possible example of calling Claude from Java.
 *
 * <p>This class sends a single question to Claude and prints the answer.
 * There is no loop, no conversation history — just one request, one response.
 * It's a good starting point before looking at {@link Main}, which adds a
 * chat loop on top of the same basic idea.
 */
public class FirstClaudeCall {

    public static void main(String[] args) {
        // AnthropicOkHttpClient.fromEnv() builds a client that automatically reads
        // your API key from the ANTHROPIC_API_KEY environment variable. You must
        // set that environment variable before running this program (see the
        // project README), otherwise the SDK will throw an error when it tries
        // to make a request.
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        // MessageCreateParams describes the request we want to send to the
        // Messages API — Claude's main API for having a conversation.
        MessageCreateParams params = MessageCreateParams.builder()
                // model: which Claude model should answer. Model.CLAUDE_HAIKU_4_5
                // is fast and inexpensive, which makes it a good choice while
                // you're learning and running the program many times.
                .model(Model.CLAUDE_HAIKU_4_5)

                // maxTokens: the maximum number of tokens (roughly, pieces of
                // words) Claude is allowed to use in its reply. If Claude's
                // answer would be longer than this, the response gets cut off.
                // 1024 is plenty for a short explanation like this one.
                .maxTokens(1024L)

                // addUserMessage(...) adds one message to the conversation with
                // the role "user" — this is the question we are asking Claude.
                // Since this is a one-off call, we only add a single user message.
                .addUserMessage("Explain Java inheritance in simple words with one code example.")
                .build();

        // Send the request to Claude and wait for the response.
        Message response = client.messages().create(params);

        // response.content() is a list of "content blocks". A simple text
        // reply comes back as a single text block, but the API technically
        // allows multiple blocks (for example, if tools were involved), so we
        // loop through all of them and print only the text ones.
        for (ContentBlock block : response.content()) {
            // block.text() returns an Optional<TextBlock>: present if this
            // block is text, empty otherwise. ifPresent(...) only runs the
            // print statement when there is actually text to print.
            block.text().ifPresent(textBlock -> System.out.println(textBlock.text()));
        }
    }
}
