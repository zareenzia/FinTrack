package org.example.finzin.voice;

import org.example.finzin.entity.VoiceSettingsEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VoiceController falls back to userId=1L when unauthenticated (see {@code getUserId}) rather than
 * rejecting the request — the "unauthenticated" cases below assert on that documented fallback.
 */
@WebMvcTest(controllers = VoiceController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class VoiceControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long DEFAULT_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private VoiceService voiceService;

    // ── POST /api/voice/parse ────────────────────────────────────────────────

    @Test
    void parseReturnsBadRequestWhenTranscriptMissing() throws Exception {
        mockMvc.perform(post("/api/voice/parse")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("transcript is required"));
    }

    @Test
    void parseReturnsBadRequestWhenTranscriptIsBlank() throws Exception {
        mockMvc.perform(post("/api/voice/parse")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("transcript is required"));
    }

    @Test
    void parseReturnsResultFromServiceOnSuccess() throws Exception {
        VoiceResultDTO result = new VoiceResultDTO(5L, "EXPENSE", Map.of("amount", 500.0), List.of(),
                null, 0.9, "heuristic", true, false, null);
        when(voiceService.parseCommand(eq(USER_ID), eq("spent five hundred on food"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/voice/parse")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"spent five hundred on food\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyId").value(5))
                .andExpect(jsonPath("$.intent").value("EXPENSE"))
                .andExpect(jsonPath("$.isComplete").value(true))
                .andExpect(jsonPath("$.giveUp").value(false))
                .andExpect(jsonPath("$.confidence").value(0.9))
                .andExpect(jsonPath("$.source").value("heuristic"));
    }

    @Test
    void parseDefaultsToUserOneWhenNotAuthenticated() throws Exception {
        VoiceResultDTO result = new VoiceResultDTO(1L, "UNKNOWN", Map.of(), List.of(), null, 0.0, "heuristic", true, true, null);
        when(voiceService.parseCommand(eq(DEFAULT_USER_ID), any(), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/voice/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"blah\"}"))
                .andExpect(status().isOk());

        verify(voiceService).parseCommand(eq(DEFAULT_USER_ID), eq("blah"), isNull());
    }

    // ── PATCH /api/voice/history/{id} ────────────────────────────────────────

    @Test
    void updateHistoryStatusReturnsBadRequestWhenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/voice/history/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("status is required"));
    }

    @Test
    void updateHistoryStatusReturns404WhenNotFound() throws Exception {
        when(voiceService.updateHistoryStatus(USER_ID, 99L, "confirmed", null, null)).thenReturn(false);

        mockMvc.perform(patch("/api/voice/history/99")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateHistoryStatusReturnsOkOnSuccess() throws Exception {
        when(voiceService.updateHistoryStatus(USER_ID, 1L, "confirmed", "TRANSACTION", 77L)).thenReturn(true);

        mockMvc.perform(patch("/api/voice/history/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\",\"resolvedEntityType\":\"TRANSACTION\",\"resolvedEntityId\":77}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ── GET /api/voice/history ───────────────────────────────────────────────

    @Test
    void getHistoryReturnsMappedListFromService() throws Exception {
        Map<String, Object> historyItem = new LinkedHashMap<>();
        historyItem.put("id", 3L);
        historyItem.put("originalTranscript", "add note buy milk");
        historyItem.put("intent", "NOTE");
        historyItem.put("status", "pending");
        when(voiceService.getHistory(USER_ID)).thenReturn(List.of(historyItem));

        mockMvc.perform(get("/api/voice/history").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].originalTranscript").value("add note buy milk"))
                .andExpect(jsonPath("$[0].intent").value("NOTE"))
                .andExpect(jsonPath("$[0].status").value("pending"));
    }

    // ── DELETE /api/voice/history/{id} ───────────────────────────────────────

    @Test
    void deleteHistoryItemReturns404WhenNotFound() throws Exception {
        when(voiceService.deleteHistoryItem(USER_ID, 5L)).thenReturn(false);

        mockMvc.perform(delete("/api/voice/history/5").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHistoryItemReturnsNoContentOnSuccess() throws Exception {
        when(voiceService.deleteHistoryItem(USER_ID, 5L)).thenReturn(true);

        mockMvc.perform(delete("/api/voice/history/5").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /api/voice/history ─────────────────────────────────────────────

    @Test
    void clearHistoryReturnsNoContentAndInvokesService() throws Exception {
        mockMvc.perform(delete("/api/voice/history").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(voiceService).clearHistory(USER_ID);
    }

    // ── GET /api/voice/settings ───────────────────────────────────────────────

    @Test
    void getSettingsReturnsMappedSettings() throws Exception {
        VoiceSettingsEntity entity = new VoiceSettingsEntity();
        entity.setUserId(USER_ID);
        entity.setEnabled(true);
        entity.setLanguage("en-US");
        entity.setSpeechProvider("browser");
        entity.setAutoStopSilenceSeconds(3);
        entity.setNoiseReduction(false);
        entity.setSaveAudioRecordings(false);
        entity.setMaxRecordingLengthSeconds(60);
        entity.setSpeechSpeed(1.0);
        when(voiceService.getOrDefaultSettings(USER_ID)).thenReturn(entity);

        mockMvc.perform(get("/api/voice/settings").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.language").value("en-US"))
                .andExpect(jsonPath("$.speechProvider").value("browser"))
                .andExpect(jsonPath("$.autoStopSilenceSeconds").value(3))
                .andExpect(jsonPath("$.maxRecordingLengthSeconds").value(60))
                .andExpect(jsonPath("$.speechSpeed").value(1.0));
    }

    // ── PUT /api/voice/settings ───────────────────────────────────────────────

    @Test
    void updateSettingsReturnsBadRequestWhenBodyIsNull() throws Exception {
        // A literal JSON "null" body never reaches the controller's own null check: Spring rejects a
        // null-deserialized non-optional @RequestBody with HttpMessageNotReadableException before the
        // handler method runs, so the response is a framework-generated 400 with no body (not the
        // controller's "Request body is required" JSON) and the service is never invoked.
        mockMvc.perform(put("/api/voice/settings")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(voiceService);
    }

    @Test
    void updateSettingsReturnsUpdatedSettingsOnSuccess() throws Exception {
        VoiceSettingsEntity updated = new VoiceSettingsEntity();
        updated.setUserId(USER_ID);
        updated.setEnabled(false);
        updated.setLanguage("bn-BD");
        updated.setSpeechProvider("browser");
        updated.setAutoStopSilenceSeconds(5);
        updated.setNoiseReduction(true);
        updated.setSaveAudioRecordings(false);
        updated.setMaxRecordingLengthSeconds(90);
        updated.setSpeechSpeed(1.5);
        when(voiceService.updateSettings(eq(USER_ID), eq(false), eq("bn-BD"), any(), eq(5), eq(true), any(), eq(90), eq(1.5)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/voice/settings")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"language\":\"bn-BD\",\"autoStopSilenceSeconds\":5," +
                                "\"noiseReduction\":true,\"maxRecordingLengthSeconds\":90,\"speechSpeed\":1.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.language").value("bn-BD"))
                .andExpect(jsonPath("$.autoStopSilenceSeconds").value(5))
                .andExpect(jsonPath("$.noiseReduction").value(true))
                .andExpect(jsonPath("$.maxRecordingLengthSeconds").value(90))
                .andExpect(jsonPath("$.speechSpeed").value(1.5));
    }
}
