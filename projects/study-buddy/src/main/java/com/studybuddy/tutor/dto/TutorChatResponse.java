package com.studybuddy.tutor.dto;

import java.util.List;

import com.studybuddy.tutor.Confidence;

public record TutorChatResponse(
        String answer,
        Confidence confidence,
        List<SourceReference> sources
) {
}
