package com.studybuddy.tutor.dto;

import jakarta.validation.constraints.NotBlank;

public record TutorChatRequest(

        @NotBlank(message = "question must not be blank")
        String question,

        String topic
) {
}
