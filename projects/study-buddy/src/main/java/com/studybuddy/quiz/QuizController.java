package com.studybuddy.quiz;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.quiz.dto.QuizGenerateRequest;
import com.studybuddy.quiz.dto.QuizGenerateResponse;
import com.studybuddy.quiz.dto.QuizSubmitRequest;
import com.studybuddy.quiz.dto.QuizSubmitResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/generate")
    public ResponseEntity<QuizGenerateResponse> generate(@Valid @RequestBody QuizGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.generate(request));
    }

    @PostMapping("/{quizId}/submit")
    public ResponseEntity<QuizSubmitResponse> submit(
            @PathVariable UUID quizId, @Valid @RequestBody QuizSubmitRequest request) {
        return ResponseEntity.ok(quizService.submit(quizId, request));
    }
}
