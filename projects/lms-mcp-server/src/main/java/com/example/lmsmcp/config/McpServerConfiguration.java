package com.example.lmsmcp.config;

import com.example.lmsmcp.tool.LmsMcpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

    public McpServerConfiguration() {
        log.debug("Initializing MCP Server Configuration");
    }

    @Bean
    public ToolCallbackProvider lmsToolCallbackProvider(LmsMcpTools lmsMcpTools) {
        log.debug("Registering LMS MCP tools with the MCP server");
        return MethodToolCallbackProvider.builder()
                .toolObjects(lmsMcpTools)
                .build();
    }
}
