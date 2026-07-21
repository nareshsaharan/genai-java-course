package com.studybuddy.audio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.audio.dto.AudioTranscriptionResult;
import com.studybuddy.common.exception.AudioServiceNotConfiguredException;
import com.studybuddy.common.exception.AudioTooLargeException;
import com.studybuddy.common.exception.UnsupportedAudioFormatException;

@WebMvcTest(AudioController.class)
class AudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioTranscriptionService audioTranscriptionService;

    @Test
    void transcribeReturnsOkWithTranscriptLanguageAndDuration() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "question.mp3", "audio/mpeg", "fake audio".getBytes());
        when(audioTranscriptionService.transcribe(any()))
                .thenReturn(new AudioTranscriptionResult("Explain dependency injection", "english", 4.2));

        mockMvc.perform(multipart("/api/audio/transcribe").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("Explain dependency injection"))
                .andExpect(jsonPath("$.language").value("english"))
                .andExpect(jsonPath("$.durationSeconds").value(4.2));
    }

    @Test
    void transcribeWithoutFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/audio/transcribe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transcribeWithUnsupportedFormatReturnsUnsupportedMediaType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "question.exe", "application/octet-stream", "x".getBytes());
        when(audioTranscriptionService.transcribe(any()))
                .thenThrow(new UnsupportedAudioFormatException("bad format"));

        mockMvc.perform(multipart("/api/audio/transcribe").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void transcribeWithOversizedFileReturnsPayloadTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "question.mp3", "audio/mpeg", "x".getBytes());
        when(audioTranscriptionService.transcribe(any()))
                .thenThrow(new AudioTooLargeException("too large"));

        mockMvc.perform(multipart("/api/audio/transcribe").file(file))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void transcribeWhenNotConfiguredReturnsServiceUnavailable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "question.mp3", "audio/mpeg", "x".getBytes());
        when(audioTranscriptionService.transcribe(any()))
                .thenThrow(new AudioServiceNotConfiguredException("not configured"));

        mockMvc.perform(multipart("/api/audio/transcribe").file(file))
                .andExpect(status().isServiceUnavailable());
    }
}
