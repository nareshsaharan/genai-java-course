package com.studybuddy.mcp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.mcp.dto.RecommendationToolResult;
import com.studybuddy.mcp.dto.SaveQuizResultToolResult;
import com.studybuddy.mcp.dto.StudyPlanToolResult;
import com.studybuddy.mcp.dto.TopicProgressToolResult;
import com.studybuddy.mcp.dto.WeakTopicToolResult;
import com.studybuddy.progress.ProgressService;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.StudyPlan;
import com.studybuddy.progress.dto.TopicProgressView;

/**
 * MCP-facing adapter over {@link ProgressService}. Deliberately thin: every
 * method validates its parameters, delegates to an existing service method,
 * and maps the result into a small typed record — no scoring, classification
 * or recommendation logic is duplicated here (all of that lives in
 * ProgressService / TopicProgressCalculator / TopicClassifier /
 * TopicRecommender, exactly as it does for the REST controllers).
 *
 * <p><b>studentId</b>: accepted and validated on every tool per the required
 * signature, but Study Buddy is currently a single-user application (see
 * the architecture decision in the project README) — there is no
 * per-student data partitioning anywhere in the schema. studentId is
 * therefore validated (and would be the natural seam for multi-tenancy if
 * that's added later) but does not currently filter which student's data is
 * returned; all tools operate on the one student's progress data.
 */
@Component
@Profile("mcp")
public class StudyBuddyMcpTools {

    private static final Logger log = LoggerFactory.getLogger(StudyBuddyMcpTools.class);

    private final ProgressService progressService;

    public StudyBuddyMcpTools(ProgressService progressService) {
        this.progressService = progressService;
    }

    @Tool(
            name = "getWeakTopics",
            description = "Lists every topic currently classified as weak for the student — topics with "
                    + "enough attempted quiz questions to be confident, where accuracy is below the "
                    + "configured threshold. Use this to see what the student is currently struggling with. "
                    + "Returns an empty list if nothing is classified weak yet (either no quizzes taken, or "
                    + "all topics are doing fine)."
    )
    public List<WeakTopicToolResult> getWeakTopics(
            @ToolParam(description = "Identifier of the student whose weak topics to look up") String studentId) {
        requireNonBlank(studentId, "studentId");
        log.info("mcp-tool tool=getWeakTopics");
        return progressService.getWeakTopics().stream()
                .map(StudyBuddyMcpTools::toWeakTopicResult)
                .toList();
    }

    @Tool(
            name = "getTopicProgress",
            description = "Looks up detailed quiz performance for one specific topic: correct/total question "
                    + "counts, recency-weighted accuracy, and whether it's classified weak. Use this when the "
                    + "student asks about their performance on a topic they name, rather than a general "
                    + "'what am I bad at' question (use getWeakTopics for that instead). If the topic has no "
                    + "recorded quiz attempts, returns found=false rather than an error."
    )
    public TopicProgressToolResult getTopicProgress(
            @ToolParam(description = "Identifier of the student") String studentId,
            @ToolParam(description = "Exact topic name to look up, e.g. 'Spring Boot' or 'RAG'") String topic) {
        requireNonBlank(studentId, "studentId");
        requireNonBlank(topic, "topic");
        log.info("mcp-tool tool=getTopicProgress");

        Optional<TopicProgressView> view = progressService.getTopic(topic);
        return view.map(v -> new TopicProgressToolResult(
                        v.topic(), true, v.correctCount(), v.totalCount(), v.accuracy(), v.classification().name()))
                .orElseGet(() -> new TopicProgressToolResult(topic, false, 0, 0, 0.0, "INSUFFICIENT_DATA"));
    }

    @Tool(
            name = "saveQuizResult",
            description = "⚠️ MUTATES DATA: records the outcome of a quiz attempt for one topic (how many "
                    + "questions were answered correctly out of how many total) and updates that topic's "
                    + "recency-weighted accuracy. Only call this after the student has actually completed a "
                    + "quiz and reported a real result — never call it speculatively or to test something. "
                    + "correctAnswers must be between 0 and totalQuestions inclusive; totalQuestions must be "
                    + "at least 1."
    )
    public SaveQuizResultToolResult saveQuizResult(
            @ToolParam(description = "Identifier of the student") String studentId,
            @ToolParam(description = "Topic the quiz covered, e.g. 'Spring Boot'") String topic,
            @ToolParam(description = "Number of questions answered correctly (0 or more)") int correctAnswers,
            @ToolParam(description = "Total number of questions in the quiz (at least 1)") int totalQuestions) {
        requireNonBlank(studentId, "studentId");
        requireNonBlank(topic, "topic");
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("totalQuestions must be at least 1");
        }
        if (correctAnswers < 0) {
            throw new IllegalArgumentException("correctAnswers must not be negative");
        }
        if (correctAnswers > totalQuestions) {
            throw new IllegalArgumentException("correctAnswers cannot exceed totalQuestions");
        }

        progressService.recordAttempt(topic, correctAnswers, totalQuestions);
        log.info("mcp-tool tool=saveQuizResult topic={} correctAnswers={} totalQuestions={}",
                topic, correctAnswers, totalQuestions);

        double updatedAccuracy = progressService.getTopic(topic).map(TopicProgressView::accuracy).orElse(0.0);
        String message = "Recorded " + correctAnswers + "/" + totalQuestions + " for topic '" + topic + "'.";
        return new SaveQuizResultToolResult(topic, correctAnswers, totalQuestions, updatedAccuracy, message);
    }

    @Tool(
            name = "recommendNextTopic",
            description = "Recommends the single next topic the student should study, with a short reason "
                    + "explaining why (lowest accuracy among weak topics, or chosen to rotate away from a "
                    + "recently-recommended near-tied topic). Use this for a direct 'what should I study next' "
                    + "question. Returns available=false with an explanatory reason if there isn't enough quiz "
                    + "history yet, or if no topic is currently weak — that is a normal outcome, not an error."
    )
    public RecommendationToolResult recommendNextTopic(
            @ToolParam(description = "Identifier of the student") String studentId) {
        requireNonBlank(studentId, "studentId");
        log.info("mcp-tool tool=recommendNextTopic");

        try {
            RecommendationResponse recommendation = progressService.getRecommendation();
            return new RecommendationToolResult(
                    true, recommendation.topic(), recommendation.reason(),
                    recommendation.accuracy(), recommendation.totalCount());
        } catch (ResourceNotFoundException e) {
            return new RecommendationToolResult(false, null, e.getMessage(), 0.0, 0);
        }
    }

    @Tool(
            name = "generateStudyPlan",
            description = "Builds a full study plan for the student: every weak topic plus the single "
                    + "recommended next topic (with reason), in one call. Prefer this over calling "
                    + "getWeakTopics and recommendNextTopic separately when the student asks for something "
                    + "broader like 'help me plan my studying' or 'what should I focus on'."
    )
    public StudyPlanToolResult generateStudyPlan(
            @ToolParam(description = "Identifier of the student") String studentId) {
        requireNonBlank(studentId, "studentId");
        log.info("mcp-tool tool=generateStudyPlan");

        StudyPlan plan = progressService.generateStudyPlan();
        List<WeakTopicToolResult> weakTopics = plan.weakTopics().stream()
                .map(StudyBuddyMcpTools::toWeakTopicResult)
                .toList();
        RecommendationToolResult recommendation = plan.recommendation() == null
                ? null
                : new RecommendationToolResult(
                        true, plan.recommendation().topic(), plan.recommendation().reason(),
                        plan.recommendation().accuracy(), plan.recommendation().totalCount());

        return new StudyPlanToolResult(weakTopics, recommendation, buildSummary(weakTopics, recommendation));
    }

    private static String buildSummary(List<WeakTopicToolResult> weakTopics, RecommendationToolResult recommendation) {
        if (weakTopics.isEmpty()) {
            return "No weak topics identified yet — either take some quizzes to build history, "
                    + "or great work if scores are already strong.";
        }
        if (recommendation != null) {
            return weakTopics.size() + " weak topic(s) identified. Recommended focus: " + recommendation.topic()
                    + " (" + recommendation.reason() + ")";
        }
        return weakTopics.size() + " weak topic(s) identified, but no single recommendation could be chosen.";
    }

    private static WeakTopicToolResult toWeakTopicResult(TopicProgressView view) {
        return new WeakTopicToolResult(view.topic(), view.accuracy(), view.totalCount());
    }

    private static void requireNonBlank(String value, String paramName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
    }
}
