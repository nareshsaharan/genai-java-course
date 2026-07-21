package com.studybuddy.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.studybuddy.config.properties.McpProperties;

class McpApiKeyFilterTest {

    @Test
    void requestWithoutHeaderIsRejectedWhenKeyIsConfigured() throws Exception {
        McpApiKeyFilter filter = new McpApiKeyFilter(new McpProperties("secret-123"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void requestWithWrongKeyIsRejected() throws Exception {
        McpApiKeyFilter filter = new McpApiKeyFilter(new McpProperties("secret-123"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void requestWithCorrectKeyIsAllowedThrough() throws Exception {
        McpApiKeyFilter filter = new McpApiKeyFilter(new McpProperties("secret-123"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Api-Key", "secret-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void everyRequestIsRejectedWhenNoKeyIsConfigured() throws Exception {
        McpApiKeyFilter filter = new McpApiKeyFilter(new McpProperties(""));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Api-Key", "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void requestsOutsideTheMcpPathAreNotIntercepted() throws Exception {
        McpApiKeyFilter filter = new McpApiKeyFilter(new McpProperties("secret-123"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/progress/topics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
