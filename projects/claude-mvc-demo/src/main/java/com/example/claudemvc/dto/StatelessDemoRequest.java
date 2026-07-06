package com.example.claudemvc.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What a client sends to POST /api/demo/stateless.
 *
 * <p>{@code call1Message} is sent as its own standalone request (CALL 1).
 * {@code call2Message} is then sent as a second, completely independent
 * request (CALL 2 WITHOUT HISTORY) - {@code call1Message} is never resent,
 * so you can see whether Claude "remembers" anything from call 1.
 *
 * <p>Example:
 * <pre>
 * {
 *   "call1Message": "My favorite color is blue.",
 *   "call2Message": "What is my favorite color?"
 * }
 * </pre>
 */
public record StatelessDemoRequest(

        @NotBlank(message = "call1Message must not be blank")
        String call1Message,

        @NotBlank(message = "call2Message must not be blank")
        String call2Message

) {
}
