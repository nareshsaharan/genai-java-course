package com.studybuddy.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveKeyRequest(

        @NotBlank(message = "apiKey must not be blank")
        String apiKey
) {
}
