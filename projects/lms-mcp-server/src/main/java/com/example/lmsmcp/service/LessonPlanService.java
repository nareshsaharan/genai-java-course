package com.example.lmsmcp.service;

import com.example.lmsmcp.exception.LmsException;
import com.example.lmsmcp.model.LessonPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class LessonPlanService {

    private static final Logger log = LoggerFactory.getLogger(LessonPlanService.class);

    /**
     * Builds a lesson plan for the given topic.
     *
     * @throws LmsException if topic is blank
     */
    public LessonPlanResponse generateLessonPlan(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new LmsException("Topic must not be empty.");
        }

        String normalizedTopic = topic.trim();
        log.debug("Generating lesson plan for topic: {}", normalizedTopic);

        return new LessonPlanResponse(
                normalizedTopic,
                "Understand the fundamentals and practical applications of " + normalizedTopic,
                List.of(
                        "Introduction to " + normalizedTopic,
                        "Core concepts of " + normalizedTopic,
                        "Hands-on practice with " + normalizedTopic,
                        "Common pitfalls in " + normalizedTopic),
                List.of(
                        "Explain the key principles of " + normalizedTopic,
                        "Apply " + normalizedTopic + " concepts to solve practical problems"),
                "60 minutes",
                List.of(
                        "Official documentation on " + normalizedTopic,
                        "Practice exercises for " + normalizedTopic));
    }
}
