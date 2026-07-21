package com.studybuddy.tutor;

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

import com.studybuddy.tutor.dto.SourceReference;
import com.studybuddy.tutor.dto.TutorChatResponse;

@WebMvcTest(TutorController.class)
class TutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TutorChatService tutorChatService;

    @Test
    void chatReturnsOkWithGroundedAnswer() throws Exception {
        SourceReference source = new SourceReference(
                UUID.randomUUID(), "spring-notes.pdf", 8, "Dependency injection is...", 0.86);
        when(tutorChatService.chat(any())).thenReturn(
                new TutorChatResponse("DI is a design pattern...", Confidence.HIGH, List.of(source)));

        mockMvc.perform(post("/api/tutor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Explain dependency injection", "topic": "Spring Boot"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("DI is a design pattern..."))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.sources[0].sourceFile").value("spring-notes.pdf"))
                .andExpect(jsonPath("$.sources[0].chunkIndex").value(8))
                .andExpect(jsonPath("$.sources[0].similarityScore").value(0.86));
    }

    @Test
    void blankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tutor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "   ", "topic": "Spring Boot"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
