package com.studybuddy.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.document.dto.DocumentUploadRequest;
import com.studybuddy.document.dto.DocumentUploadResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    public DocumentController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(@Valid @ModelAttribute DocumentUploadRequest request) {
        DocumentUploadResponse response = documentIngestionService.ingest(request.file(), request.topic());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
