package com.studybuddy.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.document.dto.DocumentUploadResponse;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService documentIngestionService;

    @Test
    void uploadReturnsCreatedWithIngestionResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes());
        UUID documentId = UUID.randomUUID();

        when(documentIngestionService.ingest(any(), eq("Java Basics")))
                .thenReturn(new DocumentUploadResponse(documentId, "notes.txt", 3, IngestionStatus.INGESTED));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("topic", "Java Basics"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.sourceFilename").value("notes.txt"))
                .andExpect(jsonPath("$.chunkCount").value(3))
                .andExpect(jsonPath("$.status").value("INGESTED"));
    }

    @Test
    void uploadWithoutFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload").param("topic", "Java Basics"))
                .andExpect(status().isBadRequest());
    }
}
