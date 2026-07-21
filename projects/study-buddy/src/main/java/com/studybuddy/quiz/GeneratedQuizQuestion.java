package com.studybuddy.quiz;

import java.util.List;

/**
 * Raw shape returned by {@link QuizGenerator}: just what the model produces.
 * Id, quiz association, and question index are attached afterwards by
 * {@link QuizService}.
 */
public record GeneratedQuizQuestion(String question, List<String> options, int correctOptionIndex) {
}
