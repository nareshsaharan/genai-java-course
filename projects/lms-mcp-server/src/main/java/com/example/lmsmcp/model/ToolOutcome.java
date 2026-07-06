package com.example.lmsmcp.model;

/**
 * Marker interface implemented by every possible MCP tool result.
 * Spring AI's @Tool methods must declare one concrete return type, so
 * each tool method declares this interface and returns either its
 * success DTO or an {@link ErrorResponse} at runtime.
 */
public interface ToolOutcome {
}
