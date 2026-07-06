package com.example.claudemvc.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What a client sends to POST /api/chat.
 *
 * <p>A Java record is a compact, immutable way to define a plain data
 * holder - {@code message()} is the auto-generated getter for the
 * {@code message} field.
 */
public record ChatRequest(

        @NotBlank(message = "message must not be blank")
        String message

) {
}
