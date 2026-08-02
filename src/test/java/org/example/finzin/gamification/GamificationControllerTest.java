package org.example.finzin.gamification;

import org.example.finzin.entity.GamificationSettingsEntity;
import org.example.finzin.entity.XpHistoryEntity;
import org.example.finzin.repository.StreakRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.example.finzin.repository.UserStatCounterRepository;
import org.example.finzin.repository.UserXpRepository;
import org.example.finzin.repository.XpHistoryRepository;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link GamificationController}. */
@WebMvcTest(GamificationController.class)
class GamificationControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private GamificationSettingsService settingsService;
    @MockitoBean private GamificationQueryService queryService;
    @MockitoBean private ChallengeService challengeService;
    @MockitoBean private UserAchievementRepository userAchievementRepository;
    @MockitoBean private XpHistoryRepository xpHistoryRepository;
    @MockitoBean private UserXpRepository userXpRepository;
    @MockitoBean private StreakRepository streakRepository;
    @MockitoBean private UserStatCounterRepository userStatCounterRepository;

    private GamificationSettingsEntity settings() {
        GamificationSettingsEntity e = new GamificationSettingsEntity();
        e.setUserId(USER_ID);
        e.setEnabled(true);
        e.setEnableNotifications(true);
        e.setShowDashboardWidget(true);
        e.setEnableCelebrations(true);
        e.setEnableChallenges(true);
        e.setEnableStreakTracking(true);
        e.setShowXp(true);
        return e;
    }

    // ── GET /summary ───────────────────────────────────────────────────────

    @Test
    void summary_returnsServiceMap() throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalXp", 350L);
        summary.put("currentLevel", 2);
        given(queryService.summary(USER_ID)).willReturn(summary);

        mockMvc.perform(get("/api/gamification/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").value(350))
                .andExpect(jsonPath("$.currentLevel").value(2));
    }

    // ── GET /levels ────────────────────────────────────────────────────────

    @Test
    void levels_returnsAllTenLevels() throws Exception {
        mockMvc.perform(get("/api/gamification/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].name").value("Starter"))
                .andExpect(jsonPath("$[0].xpRequired").value(0))
                .andExpect(jsonPath("$[9].name").value("Financial Master"));
    }

    // ── GET /achievements ──────────────────────────────────────────────────

    @Test
    void achievements_passesCategoryParamThrough() throws Exception {
        given(queryService.achievements(USER_ID, "SAVINGS")).willReturn(List.of(Map.of("code", "SAVER_1")));

        mockMvc.perform(get("/api/gamification/achievements").requestAttr("userId", USER_ID).param("category", "SAVINGS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SAVER_1"));

        verify(queryService).achievements(USER_ID, "SAVINGS");
    }

    @Test
    void achievements_passesNullCategory_whenOmitted() throws Exception {
        given(queryService.achievements(eq(USER_ID), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/gamification/achievements").requestAttr("userId", USER_ID))
                .andExpect(status().isOk());

        verify(queryService).achievements(eq(USER_ID), isNull());
    }

    // ── GET /challenges ────────────────────────────────────────────────────

    @Test
    void challenges_ensuresPeriodThenReturnsDefinitions() throws Exception {
        given(challengeService.getCurrentChallengesWithDefinitions(USER_ID)).willReturn(
                List.of(Map.of("code", "SAVE_100", "status", "IN_PROGRESS")));

        mockMvc.perform(get("/api/gamification/challenges").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SAVE_100"));

        verify(challengeService).ensureChallengesForCurrentPeriod(USER_ID);
        verify(challengeService).getCurrentChallengesWithDefinitions(USER_ID);
    }

    // ── GET /history ───────────────────────────────────────────────────────

    @Test
    void history_returnsMappedXpHistory() throws Exception {
        XpHistoryEntity e = new XpHistoryEntity();
        e.setId(9L);
        e.setAmount(50);
        e.setReason("DAILY_ACTIVE");
        e.setSourceType("SYSTEM");
        e.setSourceId("2026-01-01");
        e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        given(xpHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(e));

        mockMvc.perform(get("/api/gamification/history").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].amount").value(50))
                .andExpect(jsonPath("$[0].reason").value("DAILY_ACTIVE"));
    }

    // ── GET /settings ──────────────────────────────────────────────────────

    @Test
    void getSettings_returnsMappedSettings() throws Exception {
        given(settingsService.getOrDefault(USER_ID)).willReturn(settings());

        mockMvc.perform(get("/api/gamification/settings").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.showXp").value(true));
    }

    // ── PUT /settings ──────────────────────────────────────────────────────

    @Test
    void updateSettings_returnsBadRequest_whenBodyNull() throws Exception {
        // A literal JSON "null" body never reaches the controller: Spring rejects a null-deserialized
        // non-optional @RequestBody with HttpMessageNotReadableException before the handler method
        // runs, so the response is a framework-generated 400 with no body (not the controller's
        // "Request body is required" JSON) and the service is never invoked.
        mockMvc.perform(put("/api/gamification/settings").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(settingsService);
    }

    @Test
    void updateSettings_returnsUpdatedSettings_onSuccess() throws Exception {
        GamificationSettingsEntity updated = settings();
        updated.setEnabled(false);
        given(settingsService.update(USER_ID, false, true, true, true, true, true, true)).willReturn(updated);

        mockMvc.perform(put("/api/gamification/settings").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"enableNotifications\":true,\"showDashboardWidget\":true," +
                                "\"enableCelebrations\":true,\"enableChallenges\":true,\"enableStreakTracking\":true,\"showXp\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    // ── DELETE /reset ──────────────────────────────────────────────────────

    @Test
    void resetProgress_returnsNoContent_andClearsAllProgressStores() throws Exception {
        mockMvc.perform(delete("/api/gamification/reset").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(xpHistoryRepository).deleteByUserId(USER_ID);
        verify(userXpRepository).deleteByUserId(USER_ID);
        verify(userAchievementRepository).deleteByUserId(USER_ID);
        verify(streakRepository).deleteByUserId(USER_ID);
        verify(userStatCounterRepository).deleteByUserId(USER_ID);
        verify(challengeService).deleteAllForUser(USER_ID);
    }
}
