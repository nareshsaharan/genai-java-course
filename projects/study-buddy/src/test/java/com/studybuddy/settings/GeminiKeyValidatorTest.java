package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.ChatModel;

class GeminiKeyValidatorTest {

    @Test
    void acceptsAKeyWhenTheThrowawayModelRespondsSuccessfully() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        GeminiKeyValidator validator = new GeminiKeyValidator(apiKey -> throwawayModel);

        assertThatCode(() -> validator.validate("a-real-looking-gemini-key")).doesNotThrowAnyException();
    }

    @Test
    void wrapsAnAuthenticationFailureAsApiKeyValidationException() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenThrow(new AuthenticationException("API key not valid"));
        GeminiKeyValidator validator = new GeminiKeyValidator(apiKey -> throwawayModel);

        assertThatThrownBy(() -> validator.validate("bad-gemini-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("API key not valid");
    }

    @Test
    void buildsTheThrowawayModelWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        GeminiKeyValidator validator = new GeminiKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return throwawayModel;
        });

        validator.validate("the-submitted-gemini-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("the-submitted-gemini-key");
    }
}
