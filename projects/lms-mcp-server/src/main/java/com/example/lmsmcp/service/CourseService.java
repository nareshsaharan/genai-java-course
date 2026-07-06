package com.example.lmsmcp.service;

import com.example.lmsmcp.exception.LmsException;
import com.example.lmsmcp.model.CourseDetailsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory course catalog. Holds a fixed set of courses for this LMS demo
 * and validates lookups before returning data to callers.
 */
@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final Map<String, CourseDetailsResponse> coursesStore = new LinkedHashMap<>();

    public CourseService() {
        log.debug("Initializing CourseService with in-memory storage");
        seedSampleData();
    }

    private void seedSampleData() {
        coursesStore.put("course-001", new CourseDetailsResponse(
                "course-001",
                "Java Basics",
                "Learn the fundamentals of Java: syntax, OOP, and core APIs.",
                "John Doe",
                30));

        coursesStore.put("course-002", new CourseDetailsResponse(
                "course-002",
                "Spring Boot Basics",
                "Build REST applications and services using Spring Boot.",
                "Jane Smith",
                22));

        coursesStore.put("course-003", new CourseDetailsResponse(
                "course-003",
                "GenAI Basics",
                "Introduction to generative AI concepts, models, and use cases.",
                "Alex Turner",
                15));

        log.info("Seeded {} courses", coursesStore.size());
    }

    /**
     * Looks up a course by id.
     *
     * @throws LmsException if courseId is blank or no matching course exists
     */
    public CourseDetailsResponse getCourseById(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            throw new LmsException("Course ID must not be empty.");
        }

        CourseDetailsResponse course = coursesStore.get(courseId.trim());
        if (course == null) {
            log.warn("Course not found for id: {}", courseId);
            throw new LmsException("No course found with ID: " + courseId);
        }

        log.debug("Fetched course with id: {}", courseId);
        return course;
    }
}
