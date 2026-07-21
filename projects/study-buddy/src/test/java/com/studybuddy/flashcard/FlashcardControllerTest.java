package com.studybuddy.flashcard;

import static org.mockito.ArgumentMatchers.any;
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
import com.studybuddy.flashcard.dto.Flashcard;
import com.studybuddy.flashcard.dto.FlashcardGenerateResponse;

@WebMvcTest(FlashcardController.class)
class FlashcardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlashcardService flashcardService;

    @Test
    void generateReturnsOkWithCards() throws Exception {
        Flashcard card = new Flashcard(
                UUID.randomUUID(), "What is RAG?", "Retrieval augmented generation.",
                "RAG", Difficulty.MEDIUM, List.of(UUID.randomUUID()));
        when(flashcardService.generate(any())).thenReturn(new FlashcardGenerateResponse(List.of(card)));

        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "RAG", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].question").value("What is RAG?"))
                .andExpect(jsonPath("$.cards[0].difficulty").value("MEDIUM"));
    }

    @Test
    void blankTopicReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "   ", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countBelowOneReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "RAG", "count": 0, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countAboveTwentyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "RAG", "count": 21, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDifficultyValueReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "RAG", "count": 5, "difficulty": "IMPOSSIBLE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingContextReturnsNotFound() throws Exception {
        when(flashcardService.generate(any()))
                .thenThrow(new NoRelevantContextException("No relevant course content found for topic 'Quantum Computing'"));

        mockMvc.perform(post("/api/flashcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "Quantum Computing", "count": 5, "difficulty": "MEDIUM"}
                                """))
                .andExpect(status().isNotFound());
    }
}
