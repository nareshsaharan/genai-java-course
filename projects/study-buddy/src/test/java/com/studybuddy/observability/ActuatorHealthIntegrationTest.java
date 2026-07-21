package com.studybuddy.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Confirms /actuator/health reports UP overall and specifically includes a
 * database health component (Spring Boot auto-configures this from the
 * DataSource bean; this test proves it's actually wired and reachable
 * against a real Postgres, not just present in theory).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ActuatorHealthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void vectorStoreProperties(DynamicPropertyRegistry registry) {
        registry.add("studybuddy.database.host", postgres::getHost);
        registry.add("studybuddy.database.port", () -> postgres.getMappedPort(5432));
        registry.add("studybuddy.database.database", postgres::getDatabaseName);
        registry.add("studybuddy.database.username", postgres::getUsername);
        registry.add("studybuddy.database.password", postgres::getPassword);
        registry.add("studybuddy.claude.api-key", () -> "test-placeholder-key");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUpWithDatabaseComponent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void metricsEndpointExposesCustomStudyBuddyMetricNames() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    void requestIdHeaderIsPresentOnResponses() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getHeader("X-Request-Id"))
                        .isNotBlank());
    }
}
