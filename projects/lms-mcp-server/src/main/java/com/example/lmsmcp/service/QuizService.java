package com.example.lmsmcp.service;

import com.example.lmsmcp.exception.LmsException;
import com.example.lmsmcp.model.QuizDifficulty;
import com.example.lmsmcp.model.QuizQuestion;
import com.example.lmsmcp.model.QuizResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    /**
     * Generates a quiz for the given topic and difficulty.
     *
     * @throws LmsException if topic is blank, difficulty is blank, or
     *                       difficulty is not one of EASY, MEDIUM, HARD
     */
    public QuizResponse generateQuiz(String topic, String difficulty) {
        if (!StringUtils.hasText(topic)) {
            throw new LmsException("Topic must not be empty.");
        }
        if (!StringUtils.hasText(difficulty)) {
            throw new LmsException("Difficulty must not be empty. Valid values are: EASY, MEDIUM, HARD.");
        }

        QuizDifficulty parsedDifficulty;
        try {
            parsedDifficulty = QuizDifficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LmsException("Invalid difficulty '" + difficulty + "'. Valid values are: EASY, MEDIUM, HARD.");
        }

        String normalizedTopic = topic.trim();
        log.debug("Generating {} quiz for topic: {}", parsedDifficulty, normalizedTopic);

        return new QuizResponse(normalizedTopic, parsedDifficulty.name(), buildQuestions(normalizedTopic, parsedDifficulty));
    }

    private List<QuizQuestion> buildQuestions(String topic, QuizDifficulty difficulty) {
        int questionCount = switch (difficulty) {
            case EASY -> 3;
            case MEDIUM -> 4;
            case HARD -> 5;
        };

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= questionCount; i++) {
            String correctAnswer = "Correct explanation of " + topic + " concept " + i;
            questions.add(new QuizQuestion(
                    String.format("Question %d (%s): What is a key concept of %s?", i, difficulty, topic),
                    List.of(
                            correctAnswer,
                            "Unrelated distractor option",
                            "Common misconception about " + topic,
                            "None of the above"),
                    correctAnswer,
                    "This question tests understanding of " + topic + " at " + difficulty + " level."));
        }
        return questions;
    }
}
