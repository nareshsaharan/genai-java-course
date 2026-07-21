package com.studybuddy.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers {@link StudyBuddyMcpTools} with Spring AI's MCP server
 * auto-configuration. Only active under the "mcp" profile — matching
 * {@code spring.ai.mcp.server.enabled=true} in application-mcp.yml — so a
 * plain REST-only run never exposes an MCP endpoint at all.
 */
@Configuration
@Profile("mcp")
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider studyBuddyToolCallbackProvider(StudyBuddyMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
