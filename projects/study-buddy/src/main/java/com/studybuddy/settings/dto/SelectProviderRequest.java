package com.studybuddy.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectProviderRequest(

        @NotBlank(message = "provider must not be blank")
        String provider
) {
}
