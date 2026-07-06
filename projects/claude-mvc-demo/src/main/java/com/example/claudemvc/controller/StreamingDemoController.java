package com.example.claudemvc.controller;

import com.example.claudemvc.service.ClaudeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;

/**
 * Demonstrates Claude's STREAMING API over plain HTTP.
 *
 * <p>Non-streaming endpoints (like {@link ChatController}) build the whole
 * answer, then send it back as one JSON response - the client waits, then
 * gets everything at once. Streaming instead sends the answer back in small
 * pieces as Claude generates them, over the same HTTP connection, using
 * chunked transfer encoding. That does not make Claude answer faster, but
 * it makes the app FEEL faster: the client can render text the moment each
 * piece arrives, the same "typing" effect you see in claude.ai or ChatGPT,
 * instead of staring at a blank response until everything is ready.
 *
 * <p>This project uses classic, servlet-based Spring MVC
 * ({@code spring-boot-starter-web}), not the reactive WebFlux stack. The
 * servlet-friendly way to stream a response here is
 * {@link ResponseBodyEmitter}: we return it immediately, and push chunks
 * into it from a background thread as {@link ClaudeService#streamClaude}
 * calls us back with each piece of text.
 */
@RestController
@RequestMapping("/api/demo")
public class StreamingDemoController {

    private static final String PROMPT =
            "Write a beginner-friendly explanation of Spring Boot REST Controller with example.";

    private final ClaudeService claudeService;

    public StreamingDemoController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    /**
     * GET /api/demo/streaming
     *
     * <p>Try it with curl and {@code --no-buffer} so curl prints each chunk
     * as it arrives instead of waiting for the connection to close:
     * <pre>
     * curl --no-buffer http://localhost:8080/api/demo/streaming
     * </pre>
     */
    @GetMapping(value = "/streaming", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseBodyEmitter streamingDemo() {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();

        // The HTTP request-handling thread must return right away with the
        // emitter - it cannot sit and block for the whole streaming call.
        // A virtual thread (Java 21) is a cheap way to run that blocking
        // work in the background without managing a thread pool ourselves.
        Thread.ofVirtual().start(() -> {
            try {
                claudeService.streamClaude(PROMPT, chunk -> {
                    try {
                        // Sending a chunk here immediately flushes it to the
                        // client - this is what produces the chunk-by-chunk
                        // "typing" effect on the other end.
                        emitter.send(chunk, MediaType.TEXT_PLAIN);
                    } catch (IOException e) {
                        // The client most likely disconnected mid-stream.
                        // Wrapping in an unchecked exception lets it bubble
                        // out of this lambda and be handled below.
                        throw new StreamingIoException(e);
                    }
                });
                // Tells Spring MVC (and the client) that the response is
                // finished - no more chunks are coming.
                emitter.complete();
            } catch (Exception e) {
                // Handle errors gracefully: instead of leaving the client
                // hanging on an open connection, tell the emitter the
                // stream failed. Spring MVC then finishes the response with
                // an error status instead of just cutting the client off
                // silently.
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Small unchecked wrapper so an {@link IOException} raised inside the
     * {@code Consumer<String>} lambda above (which cannot declare checked
     * exceptions) can still propagate out to the surrounding try/catch.
     */
    private static class StreamingIoException extends RuntimeException {
        StreamingIoException(IOException cause) {
            super(cause);
        }
    }
}
