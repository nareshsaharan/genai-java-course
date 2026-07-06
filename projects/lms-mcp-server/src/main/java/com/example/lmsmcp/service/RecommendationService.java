package com.example.lmsmcp.service;

import com.example.lmsmcp.model.NextTopicResponse;
import com.example.lmsmcp.model.StudentProgressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final List<String> CURRICULUM = List.of(
            "java basics",
            "oop concepts",
            "spring boot",
            "rest apis",
            "database design",
            "microservices",
            "testing fundamentals",
            "deployment devops");

    private final StudentProgressService studentProgressService;

    public RecommendationService(StudentProgressService studentProgressService) {
        this.studentProgressService = studentProgressService;
    }

    /**
     * Recommends the next topic for a student.
     *
     * @throws com.example.lmsmcp.exception.LmsException if studentId is blank
     *                                                    or no matching student exists
     *                                                    (propagated from StudentProgressService)
     */
    public NextTopicResponse recommendNextTopic(String studentId) {
        StudentProgressResponse progress = studentProgressService.getProgress(studentId);

        List<String> completed = progress.completedTopics();
        String nextTopic = CURRICULUM.stream()
                .filter(topic -> !completed.contains(topic))
                .findFirst()
                .orElse(null);

        if (nextTopic == null) {
            log.info("Student {} has completed the full curriculum", studentId);
            return new NextTopicResponse(
                    studentId,
                    "No further topics available",
                    "Student has completed all topics in the curriculum",
                    "N/A");
        }

        log.debug("Recommending topic '{}' for student {}", nextTopic, studentId);
        return new NextTopicResponse(
                studentId,
                nextTopic,
                "Next topic in the learning sequence after: " +
                        (completed.isEmpty() ? "no prior topics" : String.join(", ", completed)),
                determineDifficulty(completed.size()));
    }

    private String determineDifficulty(int completedCount) {
        if (completedCount < 2) {
            return "EASY";
        } else if (completedCount < 5) {
            return "MEDIUM";
        }
        return "HARD";
    }
}
