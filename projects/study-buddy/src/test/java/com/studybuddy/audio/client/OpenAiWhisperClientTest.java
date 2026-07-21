package com.studybuddy.audio.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studybuddy.common.exception.AudioServiceNotConfiguredException;
import com.studybuddy.config.properties.AudioProperties;
import com.studybuddy.settings.RuntimeSecretsService;

class OpenAiWhisperClientTest {

    private AudioProperties properties() {
        return new AudioProperties(null, "whisper-1", 10_485_760, 120, 30, 2, false, "./data/audio-recordings", "");
    }

    @Test
    void throwsAudioServiceNotConfiguredWhenRuntimeSecretsHasNoOpenAiKey() {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn(null);
        OpenAiWhisperClient client = new OpenAiWhisperClient(properties(), new ObjectMapper(), secrets);

        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2, 3}, "question.wav", "audio/wav"))
                .isInstanceOf(AudioServiceNotConfiguredException.class);
    }

    @Test
    void throwsAudioServiceNotConfiguredWhenRuntimeSecretsHasBlankOpenAiKey() {
        RuntimeSecretsService secrets = mock(RuntimeSecretsService.class);
        when(secrets.getOpenAiKey()).thenReturn("   ");
        OpenAiWhisperClient client = new OpenAiWhisperClient(properties(), new ObjectMapper(), secrets);

        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2, 3}, "question.wav", "audio/wav"))
                .isInstanceOf(AudioServiceNotConfiguredException.class);
    }
}
