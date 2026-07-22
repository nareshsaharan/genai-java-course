package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

class GroqKeyValidatorTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> responseWith(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void acceptsAKeyWhenTheModelsEndpointReturns200() {
        GroqKeyValidator validator = new GroqKeyValidator(apiKey -> responseWith(200, "{\"data\":[]}"));

        assertThatCode(() -> validator.validate("gsk_a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAKeyWhenTheModelsEndpointReturns401() {
        GroqKeyValidator validator = new GroqKeyValidator(
                apiKey -> responseWith(401, "{\"error\":{\"message\":\"Invalid API Key\"}}"));

        assertThatThrownBy(() -> validator.validate("gsk_bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Invalid API Key");
    }

    @Test
    void buildsTheProbeWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        GroqKeyValidator validator = new GroqKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return responseWith(200, "{}");
        });

        validator.validate("gsk_the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("gsk_the-submitted-key");
    }
}
