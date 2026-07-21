package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

class OpenAiKeyValidatorTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> responseWith(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void acceptsAKeyWhenTheModelsEndpointReturns200() {
        OpenAiKeyValidator validator = new OpenAiKeyValidator(apiKey -> responseWith(200, "{\"data\":[]}"));

        assertThatCode(() -> validator.validate("sk-a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAKeyWhenTheModelsEndpointReturns401() {
        OpenAiKeyValidator validator = new OpenAiKeyValidator(
                apiKey -> responseWith(401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        assertThatThrownBy(() -> validator.validate("sk-bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Incorrect API key provided");
    }

    @Test
    void buildsTheProbeWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        OpenAiKeyValidator validator = new OpenAiKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return responseWith(200, "{}");
        });

        validator.validate("sk-the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("sk-the-submitted-key");
    }
}
