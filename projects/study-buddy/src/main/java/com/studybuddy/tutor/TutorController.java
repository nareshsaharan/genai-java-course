package com.studybuddy.tutor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.tutor.dto.TutorChatRequest;
import com.studybuddy.tutor.dto.TutorChatResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    private final TutorChatService tutorChatService;

    public TutorController(TutorChatService tutorChatService) {
        this.tutorChatService = tutorChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<TutorChatResponse> chat(@Valid @RequestBody TutorChatRequest request) {
        return ResponseEntity.ok(tutorChatService.chat(request));
    }
}
