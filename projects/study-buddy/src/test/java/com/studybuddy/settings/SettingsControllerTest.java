package com.studybuddy.settings;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private GroqKeyValidator groqKeyValidator;

    @MockitoBean
    private OpenRouterKeyValidator openRouterKeyValidator;

    @MockitoBean
    private GeminiKeyValidator geminiKeyValidator;

    @MockitoBean
    private OpenAiKeyValidator openAiKeyValidator;

    private void stubUnconfigured() {
        RuntimeSecretsService.KeyStatus unconfigured = new RuntimeSecretsService.KeyStatus(false, "mock", null);
        when(secrets.getClaudeStatus()).thenReturn(unconfigured);
        when(secrets.getGroqStatus()).thenReturn(unconfigured);
        when(secrets.getOpenRouterStatus()).thenReturn(unconfigured);
        when(secrets.getGeminiStatus()).thenReturn(unconfigured);
        when(secrets.getOpenAiStatus()).thenReturn(unconfigured);
        when(secrets.getChatProvider()).thenReturn(ChatProvider.CLAUDE);
        when(secrets.getEmbeddingProvider()).thenReturn(EmbeddingProvider.OPENAI);
    }

    @Test
    void getStatusReturnsEveryProvidersCurrentStateAndSelection() throws Exception {
        stubUnconfigured();
        when(secrets.getClaudeStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-ant...ab12"));

        mockMvc.perform(get("/api/settings/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatProvider").value("claude"))
                .andExpect(jsonPath("$.embeddingProvider").value("openai"))
                .andExpect(jsonPath("$.claude.configured").value(true))
                .andExpect(jsonPath("$.claude.source").value("saved"))
                .andExpect(jsonPath("$.claude.maskedKey").value("sk-ant...ab12"))
                .andExpect(jsonPath("$.groq.configured").value(false))
                .andExpect(jsonPath("$.openrouter.configured").value(false))
                .andExpect(jsonPath("$.gemini.configured").value(false))
                .andExpect(jsonPath("$.openai.configured").value(false));
    }

    @Test
    void savingAValidClaudeKeyValidatesThenPersistsIt() throws Exception {
        stubUnconfigured();
        when(secrets.getClaudeStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-ant...wxyz"));

        mockMvc.perform(put("/api/settings/keys/claude")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-a-real-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("saved"));

        verify(anthropicKeyValidator).validate("sk-ant-a-real-key");
        verify(secrets).setClaudeKey("sk-ant-a-real-key");
    }

    @Test
    void savingAnInvalidClaudeKeyReturns422AndNeverPersistsIt() throws Exception {
        doThrow(new ApiKeyValidationException("Anthropic rejected this key: invalid x-api-key"))
                .when(anthropicKeyValidator).validate("sk-ant-bad-key");

        mockMvc.perform(put("/api/settings/keys/claude")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-ant-bad-key\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Anthropic rejected this key: invalid x-api-key"));

        verify(secrets, never()).setClaudeKey(eq("sk-ant-bad-key"));
    }

    @Test
    void savingABlankKeyReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/keys/claude")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingAKeyForAnUnknownProviderReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/keys/bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"some-key\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingAValidGroqKeyValidatesThenPersistsIt() throws Exception {
        stubUnconfigured();
        when(secrets.getGroqStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "gsk_...wxyz"));

        mockMvc.perform(put("/api/settings/keys/groq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"gsk_a-real-key\"}"))
                .andExpect(status().isOk());

        verify(groqKeyValidator).validate("gsk_a-real-key");
        verify(secrets).setGroqKey("gsk_a-real-key");
    }

    @Test
    void savingAValidOpenRouterKeyValidatesThenPersistsIt() throws Exception {
        stubUnconfigured();
        when(secrets.getOpenRouterStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-or...wxyz"));

        mockMvc.perform(put("/api/settings/keys/openrouter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-or-a-real-key\"}"))
                .andExpect(status().isOk());

        verify(openRouterKeyValidator).validate("sk-or-a-real-key");
        verify(secrets).setOpenRouterKey("sk-or-a-real-key");
    }

    @Test
    void savingAValidGeminiKeyValidatesThenPersistsIt() throws Exception {
        stubUnconfigured();
        when(secrets.getGeminiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "AIza...wxyz"));

        mockMvc.perform(put("/api/settings/keys/gemini")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIza-a-real-key\"}"))
                .andExpect(status().isOk());

        verify(geminiKeyValidator).validate("AIza-a-real-key");
        verify(secrets).setGeminiKey("AIza-a-real-key");
    }

    @Test
    void savingAValidOpenAiKeyValidatesThenPersistsIt() throws Exception {
        stubUnconfigured();
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(true, "saved", "sk-...wxyz"));

        mockMvc.perform(put("/api/settings/keys/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-a-real-key\"}"))
                .andExpect(status().isOk());

        verify(openAiKeyValidator).validate("sk-a-real-key");
        verify(secrets).setOpenAiKey("sk-a-real-key");
    }

    @Test
    void clearingClaudeKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getClaudeStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "mock", null));

        mockMvc.perform(delete("/api/settings/keys/claude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearClaudeKey();
    }

    @Test
    void clearingOpenAiKeyRevertsAndReturnsUpdatedStatus() throws Exception {
        when(secrets.getOpenAiStatus()).thenReturn(new RuntimeSecretsService.KeyStatus(false, "mock", null));

        mockMvc.perform(delete("/api/settings/keys/openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        verify(secrets).clearOpenAiKey();
    }

    @Test
    void selectingAChatProviderPersistsItAndReturnsFullStatus() throws Exception {
        stubUnconfigured();
        when(secrets.getChatProvider()).thenReturn(ChatProvider.GROQ);

        mockMvc.perform(put("/api/settings/chat-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"groq\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatProvider").value("groq"));

        verify(secrets).setChatProvider(ChatProvider.GROQ);
    }

    @Test
    void selectingAnUnknownChatProviderReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/chat-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"bogus\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selectingAnEmbeddingProviderPersistsItAndReturnsFullStatus() throws Exception {
        stubUnconfigured();
        when(secrets.getEmbeddingProvider()).thenReturn(EmbeddingProvider.GEMINI);

        mockMvc.perform(put("/api/settings/embedding-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"gemini\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingProvider").value("gemini"));

        verify(secrets).setEmbeddingProvider(EmbeddingProvider.GEMINI);
    }

    @Test
    void selectingAnUnknownEmbeddingProviderReturns400() throws Exception {
        mockMvc.perform(put("/api/settings/embedding-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"bogus\"}"))
                .andExpect(status().isBadRequest());
    }
}
