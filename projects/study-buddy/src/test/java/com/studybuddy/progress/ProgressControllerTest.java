package com.studybuddy.progress;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.common.exception.ResourceNotFoundException;
import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.TopicProgressView;

@WebMvcTest(ProgressController.class)
class ProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressService progressService;

    @Test
    void topicsReturnsOkWithAllTopics() throws Exception {
        when(progressService.getTopics()).thenReturn(List.of(
                new TopicProgressView("RAG", 8, 10, 0.8, Instant.now(), TopicClassification.NOT_WEAK)));

        mockMvc.perform(get("/api/progress/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("RAG"))
                .andExpect(jsonPath("$[0].classification").value("NOT_WEAK"));
    }

    @Test
    void weakTopicsReturnsOkWithOnlyWeakTopics() throws Exception {
        when(progressService.getWeakTopics()).thenReturn(List.of(
                new TopicProgressView("Recursion", 2, 6, 0.33, Instant.now(), TopicClassification.WEAK)));

        mockMvc.perform(get("/api/progress/weak-topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("Recursion"))
                .andExpect(jsonPath("$[0].classification").value("WEAK"));
    }

    @Test
    void recommendationReturnsOkWithReason() throws Exception {
        when(progressService.getRecommendation()).thenReturn(
                new RecommendationResponse("Recursion", "Lowest accuracy among weak topics (20% over 6 questions).", 0.2, 6));

        mockMvc.perform(get("/api/progress/recommendation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Recursion"))
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }

    @Test
    void recommendationWithNoDataReturnsNotFound() throws Exception {
        when(progressService.getRecommendation()).thenThrow(new ResourceNotFoundException("no data"));

        mockMvc.perform(get("/api/progress/recommendation"))
                .andExpect(status().isNotFound());
    }
}
