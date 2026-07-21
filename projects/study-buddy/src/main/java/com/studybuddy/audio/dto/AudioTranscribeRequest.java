package com.studybuddy.audio.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

public record AudioTranscribeRequest(

        @NotNull(message = "file is required")
        MultipartFile file
) {
}
