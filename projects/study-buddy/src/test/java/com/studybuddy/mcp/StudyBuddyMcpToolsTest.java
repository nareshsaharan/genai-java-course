package com.studybuddy.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.mcp.dto.RecommendationToolResult;
import com.studybuddy.mcp.dto.SaveQuizResultToolResult;
import com.studybuddy.mcp.dto.StudyPlanToolResult;
import com.studybuddy.mcp.dto.TopicProgressToolResult;
import com.studybuddy.mcp.dto.WeakTopicToolResult;
import com.studybuddy.progress.ProgressService;
import com.studybuddy.progress.TopicClassification;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.StudyPlan;
import com.studybuddy.progress.dto.TopicProgressView;

class StudyBuddyMcpToolsTest {

    private final ProgressService progressService = mock(ProgressService.class);
    private final StudyBuddyMcpTools tools = new StudyBuddyMcpTools(progressService);

    private static TopicProgressView view(String topic, int correct, int total, double accuracy, TopicClassification c) {
        return new TopicProgressView(topic, correct, total, accuracy, Instant.now(), c);
    }

    // ---------- getWeakTopics ----------

    @Test
    void getWeakTopicsDelegatesToProgressServiceAndMapsResults() {
        when(progressService.getWeakTopics()).thenReturn(List.of(
                view("Recursion", 2, 6, 0.33, TopicClassification.WEAK)));

        List<WeakTopicToolResult> result = tools.getWeakTopics("student-1");

        assertThat(result).containsExactly(new WeakTopicToolResult("Recursion", 0.33, 6));
    }

    @Test
    void getWeakTopicsRejectsBlankStudentId() {
        assertThatThrownBy(() -> tools.getWeakTopics("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- getTopicProgress ----------

    @Test
    void getTopicProgressReturnsFoundResultWhenTopicExists() {
        when(progressService.getTopic("RAG")).thenReturn(Optional.of(
                view("RAG", 8, 10, 0.8, TopicClassification.NOT_WEAK)));

        TopicProgressToolResult result = tools.getTopicProgress("student-1", "RAG");

        assertThat(result.found()).isTrue();
        assertThat(result.correctCount()).isEqualTo(8);
        assertThat(result.classification()).isEqualTo("NOT_WEAK");
    }

    @Test
    void getTopicProgressReturnsNotFoundResultRatherThanThrowingWhenNoData() {
        when(progressService.getTopic("Quantum Computing")).thenReturn(Optional.empty());

        TopicProgressToolResult result = tools.getTopicProgress("student-1", "Quantum Computing");

        assertThat(result.found()).isFalse();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void getTopicProgressRejectsBlankTopic() {
        assertThatThrownBy(() -> tools.getTopicProgress("student-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- saveQuizResult ----------

    @Test
    void saveQuizResultDelegatesToRecordAttemptAndReturnsUpdatedAccuracy() {
        when(progressService.getTopic("RAG")).thenReturn(Optional.of(
                view("RAG", 6, 8, 0.75, TopicClassification.NOT_WEAK)));

        SaveQuizResultToolResult result = tools.saveQuizResult("student-1", "RAG", 3, 4);

        verify(progressService).recordAttempt("RAG", 3, 4);
        assertThat(result.updatedAccuracy()).isEqualTo(0.75);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void saveQuizResultRejectsCorrectAnswersGreaterThanTotal() {
        assertThatThrownBy(() -> tools.saveQuizResult("student-1", "RAG", 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        verify(progressService, never()).recordAttempt(eq("RAG"), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void saveQuizResultRejectsZeroTotalQuestions() {
        assertThatThrownBy(() -> tools.saveQuizResult("student-1", "RAG", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveQuizResultRejectsNegativeCorrectAnswers() {
        assertThatThrownBy(() -> tools.saveQuizResult("student-1", "RAG", -1, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveQuizResultRejectsBlankTopic() {
        assertThatThrownBy(() -> tools.saveQuizResult("student-1", " ", 3, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- recommendNextTopic ----------

    @Test
    void recommendNextTopicReturnsAvailableResultWhenRecommendationExists() {
        when(progressService.getRecommendation()).thenReturn(
                new RecommendationResponse("Recursion", "Lowest accuracy among weak topics (33% over 6 questions).", 0.33, 6));

        RecommendationToolResult result = tools.recommendNextTopic("student-1");

        assertThat(result.available()).isTrue();
        assertThat(result.topic()).isEqualTo("Recursion");
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void recommendNextTopicReturnsUnavailableResultRatherThanThrowingWhenNoneExists() {
        when(progressService.getRecommendation()).thenThrow(new ResourceNotFoundException("no weak topics"));

        RecommendationToolResult result = tools.recommendNextTopic("student-1");

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    // ---------- generateStudyPlan ----------

    @Test
    void generateStudyPlanComposesWeakTopicsAndRecommendation() {
        when(progressService.generateStudyPlan()).thenReturn(new StudyPlan(
                List.of(view("Recursion", 2, 6, 0.33, TopicClassification.WEAK)),
                new RecommendationResponse("Recursion", "Lowest accuracy among weak topics.", 0.33, 6)));

        StudyPlanToolResult result = tools.generateStudyPlan("student-1");

        assertThat(result.weakTopics()).containsExactly(new WeakTopicToolResult("Recursion", 0.33, 6));
        assertThat(result.recommendation().available()).isTrue();
        assertThat(result.recommendation().topic()).isEqualTo("Recursion");
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    void generateStudyPlanHandlesNoWeakTopicsAndNoRecommendation() {
        when(progressService.generateStudyPlan()).thenReturn(new StudyPlan(List.of(), null));

        StudyPlanToolResult result = tools.generateStudyPlan("student-1");

        assertThat(result.weakTopics()).isEmpty();
        assertThat(result.recommendation()).isNull();
        assertThat(result.summary()).isNotBlank();
    }
}
