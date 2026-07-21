package com.studybuddy.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the full application with the "mcp" profile active (real Postgres +
 * real MCP server auto-configuration + our filter/tool wiring) to prove the
 * profile gating and API-key protection actually work together at startup —
 * not just as isolated unit-tested pieces. Claude/Anthropic is never
 * involved: nothing here calls the chat model.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mcp")
@Testcontainers
class McpServerIntegrationTest {

    private static final String TEST_API_KEY = "test-mcp-key-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("studybuddy.database.host", postgres::getHost);
        registry.add("studybuddy.database.port", () -> postgres.getMappedPort(5432));
        registry.add("studybuddy.database.database", postgres::getDatabaseName);
        registry.add("studybuddy.database.username", postgres::getUsername);
        registry.add("studybuddy.database.password", postgres::getPassword);
        registry.add("studybuddy.claude.api-key", () -> "test-placeholder-key");
        registry.add("studybuddy.mcp.api-key", () -> TEST_API_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mcpEndpointRejectsRequestsWithoutTheApiKeyHeader() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpointRejectsRequestsWithTheWrongApiKey() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header("X-MCP-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpointAcceptsRequestsWithTheCorrectApiKey() throws Exception {
        // Asserting only that the request clears our filter (i.e. is not
        // 401/503) — the exact JSON-RPC response shape is the MCP SDK's
        // concern, not something this project's own code produces, and the
        // MCP wire protocol is still evolving quickly enough that pinning
        // an exact response body here would be brittle.
        mockMvc.perform(post("/mcp")
                        .header("X-MCP-Api-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status)
                            .as("request should clear the API-key filter")
                            .isNotIn(401, 503);
                });
    }

    @Test
    void restEndpointsAreUnaffectedByTheMcpApiKeyFilter() throws Exception {
        // The filter only intercepts /mcp — normal REST traffic must not
        // suddenly require the MCP API key just because the "mcp" profile
        // is active.
        mockMvc.perform(post("/api/tutor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
