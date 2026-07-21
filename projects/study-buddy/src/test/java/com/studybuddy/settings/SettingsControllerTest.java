package com.studybuddy.settings;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.studybuddy.common.exception.ApiKeyValidationException;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeSecretsService secrets;

    @MockitoBean
    private AnthropicKeyValidator anthropicKeyValidator;

    @MockitoBean
    private OpenAiKeyValidator openAiKeyValidator;

    @Test
    void getStatusReturnsBothProvidersCurrentState() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "env", "sk-ant...ab12"));
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(get("/api/settings/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anthropic.configured").value(true))
                .andExpect(jsonPath("$.anthropic.source").value("env"))
                .andExpect(jsonPath("$.anthropic.maskedKey").value("sk-ant...ab12"))
                .andExpect(jsonPath("$.openai.configured").value(false));
    }

    @Test
    void savingAValidAnthropicKeyValidatesThenPersistsIt() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-ant...wxyz"));

        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-a-real-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("saved"));

        verify(anthropicKeyValidator).validate("sk-ant-a-real-key");
        verify(secrets).setAnthropicKey("sk-ant-a-real-key");
    }

    @Test
    void savingAnInvalidAnthropicKeyReturns422AndNeverPersistsIt() throws Exception {
        doThrow(new ApiKeyValidationException("Anthropic rejected this key: invalid x-api-key"))
                .when(anthropicKeyValidator).validate("sk-ant-bad-key");

        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-bad-key\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Anthropic rejected this key: invalid x-api-key"));

        verify(secrets, org.mockito.Mockito.never()).setAnthropicKey(eq("sk-ant-bad-key"));
    }

    @Test
    void savingABlankAnthropicKeyReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/keys/anthropic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingAValidOpenAiKeyValidatesThenPersistsIt() throws Exception {
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-...wxyz"));

        mockMvc.perform(put("/api/settings/keys/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-a-real-key\"}"))
                .andExpect(status().isOk());

        verify(openAiKeyValidator).validate("sk-a-real-key");
        verify(secrets).setOpenAiKey("sk-a-real-key");
    }

    @Test
    void clearingAnthropicKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getAnthropicStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(delete("/api/settings/keys/anthropic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearAnthropicKey();
    }

    @Test
    void clearingOpenAiKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "none", null));

        mockMvc.perform(delete("/api/settings/keys/openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearOpenAiKey();
    }
}
