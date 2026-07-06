package com.example.lmsmcp.service;

import com.example.lmsmcp.exception.LmsException;
import com.example.lmsmcp.model.StudentProgressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory student progress tracker. Holds a fixed set of students for
 * this LMS demo and validates lookups before returning data to callers.
 */
@Service
public class StudentProgressService {

    private static final Logger log = LoggerFactory.getLogger(StudentProgressService.class);

    private final Map<String, StudentProgressResponse> progressStore = new LinkedHashMap<>();

    public StudentProgressService() {
        log.debug("Initializing StudentProgressService with in-memory storage");
        seedSampleData();
    }

    private void seedSampleData() {
        progressStore.put("student-001", new StudentProgressResponse(
                "student-001",
                "Alice Johnson",
                List.of("java basics", "oop concepts"),
                "spring boot",
                40.0,
                "2026-07-01"));

        progressStore.put("student-002", new StudentProgressResponse(
                "student-002",
                "Brian Lee",
                List.of("java basics"),
                "oop concepts",
                20.0,
                "2026-06-28"));

        progressStore.put("student-003", new StudentProgressResponse(
                "student-003",
                "Carol Smith",
                List.of("java basics", "oop concepts", "spring boot", "rest apis"),
                "database design",
                55.0,
                "2026-07-03"));

        log.info("Seeded {} student progress records", progressStore.size());
    }

    /**
     * Looks up a student's progress by id.
     *
     * @throws LmsException if studentId is blank or no matching student exists
     */
    public StudentProgressResponse getProgress(String studentId) {
        if (!StringUtils.hasText(studentId)) {
            throw new LmsException("Student ID must not be empty.");
        }

        StudentProgressResponse progress = progressStore.get(studentId.trim());
        if (progress == null) {
            log.warn("Student not found for id: {}", studentId);
            throw new LmsException("No student found with ID: " + studentId);
        }

        log.debug("Fetched progress for student: {}", studentId);
        return progress;
    }
}
