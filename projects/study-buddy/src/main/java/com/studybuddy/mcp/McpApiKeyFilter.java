package com.studybuddy.mcp;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.studybuddy.config.properties.McpProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gates the entire MCP endpoint (not just {@code saveQuizResult}) behind a
 * shared-secret header. MCP multiplexes every tool call — read and write —
 * over one JSON-RPC endpoint, so there's no protocol-level way to challenge
 * only the mutating tool without parsing every request body; protecting the
 * whole endpoint is the simplest mechanism that's still robust to MCP
 * protocol/spec changes. Only registered under the "mcp" profile.
 *
 * <p>Fails closed: if {@code MCP_API_KEY} isn't configured, every request is
 * rejected (503) rather than silently allowing unauthenticated access.
 */
@Component
@Profile("mcp")
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyFilter.class);
    private static final String HEADER_NAME = "X-MCP-Api-Key";
    private static final String MCP_PATH_PREFIX = "/mcp";

    private final McpProperties properties;

    public McpApiKeyFilter(McpProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(MCP_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(properties.apiKey())) {
            log.error("MCP_API_KEY is not configured; refusing all MCP requests");
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP server is not configured");
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        if (providedKey == null || !constantTimeEquals(providedKey, properties.apiKey())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid " + HEADER_NAME + " header");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Avoids leaking key length/content via response-time differences. */
    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
