package com.studybuddy.audio;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.audio.dto.AudioTranscribeRequest;
import com.studybuddy.audio.dto.AudioTranscriptionResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/audio")
@Validated
public class AudioController {

    private final AudioTranscriptionService audioTranscriptionService;

    public AudioController(AudioTranscriptionService audioTranscriptionService) {
        this.audioTranscriptionService = audioTranscriptionService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioTranscriptionResult> transcribe(@Valid @ModelAttribute AudioTranscribeRequest request) {
        return ResponseEntity.ok(audioTranscriptionService.transcribe(request.file()));
    }
}
