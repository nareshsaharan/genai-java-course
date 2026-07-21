package com.studybuddy.quiz;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.common.exception.NoRelevantContextException;
import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.quiz.dto.QuizAnswerResult;
import com.studybuddy.quiz.dto.QuizGenerateResponse;
import com.studybuddy.quiz.dto.QuizQuestionView;
import com.studybuddy.quiz.dto.QuizSubmitResponse;

@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizService quizService;

    @Test
    void generateReturnsCreatedWithoutCorrectAnswerField() throws Exception {
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(quizService.generate(any())).thenReturn(new QuizGenerateResponse(
                quizId, "RAG", List.of(new QuizQuestionView(questionId, 0, "What is RAG?", List.of("A", "B", "C", "D")))));

        mockMvc.perform(post("/api/quizzes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "RAG", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quizId").value(quizId.toString()))
                .andExpect(jsonPath("$.questions[0].questionText").value("What is RAG?"))
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").doesNotExist());
    }

    @Test
    void generateWithBlankTopicReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/quizzes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "   ", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateWithNoRelevantContextReturnsNotFound() throws Exception {
        when(quizService.generate(any())).thenThrow(new NoRelevantContextException("no content"));

        mockMvc.perform(post("/api/quizzes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "Quantum Computing", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitReturnsOkWithRevealedAnswers() throws Exception {
        UUID quizId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(quizService.submit(eq(quizId), any())).thenReturn(new QuizSubmitResponse(
                attemptId, "RAG", 1, 1, 1.0, List.of(new QuizAnswerResult(questionId, 0, 0, true))));

        mockMvc.perform(post("/api/quizzes/" + quizId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": \"" + questionId + "\", \"selectedOptionIndex\": 0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.results[0].correctOptionIndex").value(0));
    }

    @Test
    void submitAgainstUnknownQuizReturnsNotFound() throws Exception {
        UUID quizId = UUID.randomUUID();
        when(quizService.submit(eq(quizId), any())).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(post("/api/quizzes/" + quizId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": \"" + UUID.randomUUID() + "\", \"selectedOptionIndex\": 0}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitWithEmptyAnswersReturnsBadRequest() throws Exception {
        UUID quizId = UUID.randomUUID();

        mockMvc.perform(post("/api/quizzes/" + quizId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": []}"))
                .andExpect(status().isBadRequest());
    }
}
