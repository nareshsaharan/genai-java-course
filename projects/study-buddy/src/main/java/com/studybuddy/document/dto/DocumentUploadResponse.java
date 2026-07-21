package com.studybuddy.document.dto;

import java.util.UUID;

import com.studybuddy.document.IngestionStatus;

public record DocumentUploadResponse(
        UUID documentId,
        String sourceFilename,
        int chunkCount,
        IngestionStatus status
) {
}
