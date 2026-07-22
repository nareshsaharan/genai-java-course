package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

class OpenRouterKeyValidatorTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> responseWith(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void acceptsAKeyWhenTheKeyInfoEndpointReturns200() {
        OpenRouterKeyValidator validator = new OpenRouterKeyValidator(
                apiKey -> responseWith(200, "{\"data\":{\"limit\":null,\"usage\":0}}"));

        assertThatCode(() -> validator.validate("sk-or-v1-a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAKeyWhenTheKeyInfoEndpointReturns401() {
        OpenRouterKeyValidator validator = new OpenRouterKeyValidator(
                apiKey -> responseWith(401, "{\"error\":{\"message\":\"No auth credentials found\"}}"));

        assertThatThrownBy(() -> validator.validate("sk-or-v1-bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("No auth credentials found");
    }

    @Test
    void buildsTheProbeWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        OpenRouterKeyValidator validator = new OpenRouterKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return responseWith(200, "{}");
        });

        validator.validate("sk-or-v1-the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("sk-or-v1-the-submitted-key");
    }
}
