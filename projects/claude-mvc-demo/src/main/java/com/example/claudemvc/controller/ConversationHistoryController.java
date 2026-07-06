package com.example.claudemvc.controller;

import com.example.claudemvc.dto.ConversationHistoryRequest;
import com.example.claudemvc.dto.ConversationHistoryResponse;
import com.example.claudemvc.service.ClaudeService;
import com.example.claudemvc.service.ConversationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows how a Java application - not Claude itself - is what makes a
 * conversation "remember" earlier turns.
 *
 * <p>Claude does NOT remember anything automatically between API calls (see
 * {@link StatelessDemoController} for a demo of that). Every request to the
 * Messages API is independent. The only reason a chat assistant appears to
 * have memory is that the CALLER resends the entire conversation so far -
 * every previous user message and every previous Claude reply - on each new
 * request. All of that history bookkeeping happens inside
 * {@link ClaudeService#continueConversation(String, String)}; this
 * controller only forwards the request and shapes the HTTP response.
 *
 * <p>POST /api/demo/conversation-history:
 * <ol>
 *   <li>CALL 1 - sends {@code request.firstMessage()} on its own.</li>
 *   <li>Claude's reply is stored back into the service's history list.</li>
 *   <li>CALL 2 WITH HISTORY - sends {@code request.secondMessage()} together
 *       with the full history (first message + first reply), not just the
 *       new question by itself.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/demo")
public class ConversationHistoryController {

    private final ClaudeService claudeService;

    public ConversationHistoryController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/conversation-history")
    public ConversationHistoryResponse conversationHistory(
            @Valid @RequestBody ConversationHistoryRequest request) {

        // All of the "remember the first message" logic lives in the service
        // layer, using a List<MessageParam> under the hood. See
        // ClaudeServiceImpl.continueConversation(...) for the details.
        ConversationResult result = claudeService.continueConversation(
                request.firstMessage(), request.secondMessage());

        String explanation = "Claude did not remember anything by itself. Our Spring service "
                + "kept a List<MessageParam> and resent every earlier message - including "
                + "Claude's own first reply - on the second request. That resending is the "
                + "entire mechanism behind a Claude-powered chat conversation (see "
                + "ConversationHistoryDemo.java in claude-console-demo for the same idea as a "
                + "plain Java program).";

        return new ConversationHistoryResponse(result.firstReply(), result.secondReply(), explanation);
    }
}
