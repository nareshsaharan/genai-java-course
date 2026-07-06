package com.example.lmsmcp.tool;

import com.example.lmsmcp.exception.LmsException;
import com.example.lmsmcp.model.ErrorResponse;
import com.example.lmsmcp.model.ToolOutcome;
import com.example.lmsmcp.service.CourseService;
import com.example.lmsmcp.service.LessonPlanService;
import com.example.lmsmcp.service.QuizService;
import com.example.lmsmcp.service.RecommendationService;
import com.example.lmsmcp.service.StudentProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools exposed to Claude Desktop for LMS operations.
 * This class holds no business logic or validation itself - it only
 * calls the matching service and converts a thrown {@link LmsException}
 * into a readable {@link ErrorResponse}, so the client always gets a
 * structured result instead of a raw exception.
 */
@Component
public class LmsMcpTools {

    private static final Logger log = LoggerFactory.getLogger(LmsMcpTools.class);

    private final CourseService courseService;
    private final LessonPlanService lessonPlanService;
    private final QuizService quizService;
    private final StudentProgressService studentProgressService;
    private final RecommendationService recommendationService;

    public LmsMcpTools(CourseService courseService,
                        LessonPlanService lessonPlanService,
                        QuizService quizService,
                        StudentProgressService studentProgressService,
                        RecommendationService recommendationService) {
        this.courseService = courseService;
        this.lessonPlanService = lessonPlanService;
        this.quizService = quizService;
        this.studentProgressService = studentProgressService;
        this.recommendationService = recommendationService;
    }

    @Tool(name = "getCourseDetails",
            description = "Retrieve detailed information about a course, including its name, description, instructor, and enrollment count.")
    public ToolOutcome getCourseDetails(
            @ToolParam(description = "Unique identifier of the course to look up, e.g. 'course-001'")
            String courseId) {
        try {
            return courseService.getCourseById(courseId);
        } catch (LmsException e) {
            log.warn("getCourseDetails rejected: {}", e.getMessage());
            return new ErrorResponse(e.getMessage());
        }
    }

    @Tool(name = "getLessonPlan",
            description = "Generate a structured lesson plan for a given topic, including objectives, sub-topics, learning outcomes, and resources.")
    public ToolOutcome getLessonPlan(
            @ToolParam(description = "The subject or topic to create a lesson plan for, e.g. 'Spring Boot'")
            String topic) {
        try {
            return lessonPlanService.generateLessonPlan(topic);
        } catch (LmsException e) {
            log.warn("getLessonPlan rejected: {}", e.getMessage());
            return new ErrorResponse(e.getMessage());
        }
    }

    @Tool(name = "generateQuiz",
            description = "Generate a quiz with multiple-choice questions for a given topic and difficulty level (EASY, MEDIUM, or HARD).")
    public ToolOutcome generateQuiz(
            @ToolParam(description = "The subject or topic for the quiz, e.g. 'Java Collections'")
            String topic,
            @ToolParam(description = "Difficulty level of the quiz. Must be one of: EASY, MEDIUM, HARD")
            String difficulty) {
        try {
            return quizService.generateQuiz(topic, difficulty);
        } catch (LmsException e) {
            log.warn("generateQuiz rejected: {}", e.getMessage());
            return new ErrorResponse(e.getMessage());
        }
    }

    @Tool(name = "checkStudentProgress",
            description = "Check a student's current learning progress, including completed topics, current topic, and completion percentage.")
    public ToolOutcome checkStudentProgress(
            @ToolParam(description = "Unique identifier of the student, e.g. 'student-001'")
            String studentId) {
        try {
            return studentProgressService.getProgress(studentId);
        } catch (LmsException e) {
            log.warn("checkStudentProgress rejected: {}", e.getMessage());
            return new ErrorResponse(e.getMessage());
        }
    }

    @Tool(name = "recommendNextTopic",
            description = "Recommend the next topic a student should study next, based on their completed topics and current progress.")
    public ToolOutcome recommendNextTopic(
            @ToolParam(description = "Unique identifier of the student, e.g. 'student-001'")
            String studentId) {
        try {
            return recommendationService.recommendNextTopic(studentId);
        } catch (LmsException e) {
            log.warn("recommendNextTopic rejected: {}", e.getMessage());
            return new ErrorResponse(e.getMessage());
        }
    }
}
