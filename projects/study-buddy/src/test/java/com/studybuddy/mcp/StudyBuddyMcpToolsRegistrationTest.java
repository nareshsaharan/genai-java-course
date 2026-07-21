package com.studybuddy.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.studybuddy.progress.ProgressService;
import com.studybuddy.progress.TopicClassification;
import com.studybuddy.progress.dto.TopicProgressView;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Proves the tools are actually discoverable and invocable through Spring
 * AI's own reflection/JSON-schema machinery — not just callable as plain
 * Java methods (that's covered by {@link StudyBuddyMcpToolsTest}). This is
 * what {@code McpToolConfig}'s {@code ToolCallbackProvider} bean does at
 * startup; building the provider directly here exercises the same code path
 * without needing a full Spring context or Testcontainers.
 */
class StudyBuddyMcpToolsRegistrationTest {

    private final ProgressService progressService = mock(ProgressService.class);
    private final StudyBuddyMcpTools tools = new StudyBuddyMcpTools(progressService);
    private final ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build()
            .getToolCallbacks();

    @Test
    void allFiveToolsAreRegisteredWithExpectedNames() {
        List<String> names = Stream.of(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder(
                "getWeakTopics", "getTopicProgress", "saveQuizResult", "recommendNextTopic", "generateStudyPlan");
    }

    @Test
    void everyToolHasANonBlankDescriptionForModelSelection() {
        assertThat(callbacks).allSatisfy(callback ->
                assertThat(callback.getToolDefinition().description()).isNotBlank());
    }

    @Test
    void getWeakTopicsIsInvocableThroughTheRealToolCallbackJsonPath() {
        when(progressService.getWeakTopics()).thenReturn(List.of(
                new TopicProgressView("Recursion", 2, 6, 0.33, Instant.now(), TopicClassification.WEAK)));

        ToolCallback getWeakTopics = Stream.of(callbacks)
                .filter(c -> c.getToolDefinition().name().equals("getWeakTopics"))
                .findFirst()
                .orElseThrow();

        String jsonResult = getWeakTopics.call("{\"studentId\": \"student-1\"}");

        assertThat(jsonResult).contains("Recursion").contains("0.33");
    }

    @Test
    void saveQuizResultToolRejectsInvalidArgumentsThroughTheRealCallPath() {
        ToolCallback saveQuizResult = Stream.of(callbacks)
                .filter(c -> c.getToolDefinition().name().equals("saveQuizResult"))
                .findFirst()
                .orElseThrow();

        // correctAnswers (5) > totalQuestions (4). Verified behavior: at this
        // layer (ToolCallback.call), Spring AI 1.1.7 propagates the thrown
        // IllegalArgumentException wrapped in ToolExecutionException rather
        // than swallowing it into a JSON error payload — the MCP transport
        // one layer up is what turns this into a JSON-RPC isError result.
        assertThatThrownBy(() -> saveQuizResult.call(
                "{\"studentId\": \"student-1\", \"topic\": \"RAG\", \"correctAnswers\": 5, \"totalQuestions\": 4}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correctAnswers cannot exceed totalQuestions");
    }
}
