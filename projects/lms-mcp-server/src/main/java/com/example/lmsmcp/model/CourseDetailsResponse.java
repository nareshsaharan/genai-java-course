package com.example.lmsmcp.model;

/**
 * Response for the "getCourseDetails" tool.
 * Holds the basic information about one course: who teaches it,
 * what it's about, and how many students are enrolled.
 */
public record CourseDetailsResponse(
        String courseId,
        String courseName,
        String description,
        String instructor,
        int enrolledStudents
) implements ToolOutcome {
}
