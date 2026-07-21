package com.studybuddy.flashcard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.flashcard.dto.FlashcardGenerateRequest;
import com.studybuddy.flashcard.dto.FlashcardGenerateResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @PostMapping
    public ResponseEntity<FlashcardGenerateResponse> generate(@Valid @RequestBody FlashcardGenerateRequest request) {
        return ResponseEntity.ok(flashcardService.generate(request));
    }
}
