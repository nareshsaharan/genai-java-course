package com.example.lmsmcp.model;

/**
 * Returned by any MCP tool instead of a real response when the input
 * is invalid or the requested data cannot be found. This keeps failures
 * readable for the client instead of throwing a raw exception.
 */
public record ErrorResponse(String message) implements ToolOutcome {
}
