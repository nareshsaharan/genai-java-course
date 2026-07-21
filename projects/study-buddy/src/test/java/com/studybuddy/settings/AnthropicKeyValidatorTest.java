package com.studybuddy.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.ApiKeyValidationException;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.ChatModel;

class AnthropicKeyValidatorTest {

    @Test
    void acceptsAKeyWhenTheThrowawayModelRespondsSuccessfully() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> throwawayModel);

        assertThatCode(() -> validator.validate("sk-ant-a-real-looking-key")).doesNotThrowAnyException();
    }

    @Test
    void wrapsAnAuthenticationFailureAsApiKeyValidationException() {
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenThrow(new AuthenticationException("invalid x-api-key"));
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> throwawayModel);

        assertThatThrownBy(() -> validator.validate("sk-ant-bad-key"))
                .isInstanceOf(ApiKeyValidationException.class)
                .hasMessageContaining("invalid x-api-key");
    }

    @Test
    void buildsTheThrowawayModelWithTheSubmittedKeyNotAnyOtherKey() {
        java.util.concurrent.atomic.AtomicReference<String> keyUsed = new java.util.concurrent.atomic.AtomicReference<>();
        ChatModel throwawayModel = mock(ChatModel.class);
        when(throwawayModel.chat("Hi")).thenReturn("Hello!");
        AnthropicKeyValidator validator = new AnthropicKeyValidator(apiKey -> {
            keyUsed.set(apiKey);
            return throwawayModel;
        });

        validator.validate("sk-ant-the-submitted-key");

        org.assertj.core.api.Assertions.assertThat(keyUsed.get()).isEqualTo("sk-ant-the-submitted-key");
    }
}
