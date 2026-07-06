package com.example.claudemvc.controller;

import com.example.claudemvc.dto.StatelessDemoRequest;
import com.example.claudemvc.dto.StatelessDemoResponse;
import com.example.claudemvc.service.ClaudeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates that the Claude Messages API is STATELESS - over HTTP this
 * time, instead of a plain Java program (see StatelessDemo.java in the
 * claude-console-demo project for the same idea as a standalone script).
 *
 * <p>POST /api/demo/stateless takes two prompts from the caller and makes
 * two independent calls through {@link ClaudeService}:
 * <ol>
 *   <li>CALL 1 - {@code request.call1Message()}, e.g. "My name is Ankur."</li>
 *   <li>CALL 2 WITHOUT HISTORY - {@code request.call2Message()}, e.g.
 *       "What is my name?" (call 1's message is deliberately NOT resent)</li>
 * </ol>
 * Because the API keeps no memory between requests, and this controller
 * does not resend call 1's message, Claude has no reliable way to use
 * anything from call 1 while answering call 2.
 */
@RestController
@RequestMapping("/api/demo")
public class StatelessDemoController {

    private final ClaudeService claudeService;

    public StatelessDemoController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/stateless")
    public StatelessDemoResponse statelessDemo(@Valid @RequestBody StatelessDemoRequest request) {
        // CALL 1: send the caller's first prompt as a single, standalone
        // request. Nothing about it is stored on Claude's side after the
        // response comes back.
        String call1Reply = claudeService.askClaude(request.call1Message());

        // CALL 2 WITHOUT HISTORY: send the caller's follow-up question as a
        // brand new, independent request. We deliberately do NOT include
        // call 1's message here, so Claude has no memory of it.
        String call2Reply = claudeService.askClaude(request.call2Message());

        String explanation = "Claude could not reliably answer CALL 2 because every API "
                + "request is independent - there is no server-side memory. To keep "
                + "context across turns, the caller must resend prior messages itself "
                + "(see ChatController + a client-maintained history, or Main.java in "
                + "claude-console-demo).";

        return new StatelessDemoResponse(call1Reply, call2Reply, explanation);
    }
}
