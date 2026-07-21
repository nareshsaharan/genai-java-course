package com.studybuddy.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bound from multipart/form-data on POST /api/documents/upload: a "file"
 * part and an optional "topic" part.
 */
public record DocumentUploadRequest(

        @NotNull(message = "file is required")
        MultipartFile file,

        @Size(max = 200, message = "topic must be at most 200 characters")
        String topic
) {
}
