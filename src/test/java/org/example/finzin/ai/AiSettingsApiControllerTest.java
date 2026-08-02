package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link AiSettingsApiController}. */
@WebMvcTest(AiSettingsApiController.class)
class AiSettingsApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private AiSettingsService aiSettingsService;

    private AiSettingsEntity settings(Long userId, String model, int maxTokens, double temperature, boolean enabled) {
        AiSettingsEntity e = new AiSettingsEntity();
        e.setUserId(userId);
        e.setProvider("openai");
        e.setModel(model);
        e.setMaxTokens(maxTokens);
        e.setTemperature(temperature);
        e.setEnabled(enabled);
        e.setDeveloperMode(false);
        e.setEnableProactiveInsights(true);
        e.setEnableBudgetCoaching(true);
        e.setEnableSavingsCoaching(true);
        e.setEnableMonthlyReports(true);
        e.setEnableDashboardSummary(true);
        return e;
    }

    @Test
    void getSettings_returnsMappedSettings() throws Exception {
        given(aiSettingsService.getOrDefault(42L)).willReturn(settings(42L, "gpt-5", 800, 0.3, true));

        mockMvc.perform(get("/api/ai/settings").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("openai"))
                .andExpect(jsonPath("$.model").value("gpt-5"))
                .andExpect(jsonPath("$.maxTokens").value(800))
                .andExpect(jsonPath("$.temperature").value(0.3))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void getSettings_defaultsToUserOne_whenUserIdAttributeMissing() throws Exception {
        given(aiSettingsService.getOrDefault(1L)).willReturn(settings(1L, "gpt-5", 800, 0.3, true));

        mockMvc.perform(get("/api/ai/settings"))
                .andExpect(status().isOk());

        verify(aiSettingsService).getOrDefault(1L);
    }

    @Test
    void updateSettings_returnsBadRequest_whenBodyNull() throws Exception {
        // A literal JSON "null" body never reaches the controller's own null check: Spring rejects a
        // null-deserialized non-optional @RequestBody with HttpMessageNotReadableException before the
        // handler method runs, so the response is a framework-generated 400 with no body (not the
        // controller's "Request body is required" JSON) and the service is never invoked.
        mockMvc.perform(put("/api/ai/settings").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void updateSettings_returnsBadRequest_whenServiceRejectsRange() throws Exception {
        given(aiSettingsService.update(42L, "gpt-5", 50, 0.3, true, false, true, true, true, true, true))
                .willReturn(null);

        mockMvc.perform(put("/api/ai/settings").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5\",\"maxTokens\":50,\"temperature\":0.3,\"enabled\":true,\"developerMode\":false," +
                                "\"enableProactiveInsights\":true,\"enableBudgetCoaching\":true,\"enableSavingsCoaching\":true," +
                                "\"enableMonthlyReports\":true,\"enableDashboardSummary\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxTokens must be 100-4000 and temperature must be 0-2"));
    }

    @Test
    void updateSettings_returnsUpdatedSettings_onSuccess() throws Exception {
        given(aiSettingsService.update(42L, "gpt-5", 1200, 0.5, true, true, false, false, false, false, false))
                .willReturn(settings(42L, "gpt-5", 1200, 0.5, true));

        mockMvc.perform(put("/api/ai/settings").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5\",\"maxTokens\":1200,\"temperature\":0.5,\"enabled\":true,\"developerMode\":true," +
                                "\"enableProactiveInsights\":false,\"enableBudgetCoaching\":false,\"enableSavingsCoaching\":false," +
                                "\"enableMonthlyReports\":false,\"enableDashboardSummary\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTokens").value(1200))
                .andExpect(jsonPath("$.temperature").value(0.5));

        verify(aiSettingsService).update(42L, "gpt-5", 1200, 0.5, true, true, false, false, false, false, false);
    }
}
