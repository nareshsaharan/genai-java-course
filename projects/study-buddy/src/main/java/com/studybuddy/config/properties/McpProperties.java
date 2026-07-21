package com.studybuddy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared-secret used by {@code McpApiKeyFilter} to protect the MCP endpoint
 * (which includes {@code saveQuizResult}, the one tool that mutates data).
 * Only read when the "mcp" profile is active — see application-mcp.yml.
 */
@ConfigurationProperties(prefix = "studybuddy.mcp")
public record McpProperties(String apiKey) {
}
