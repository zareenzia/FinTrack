package org.example.finzin.gamification;

import org.example.finzin.entity.AchievementDefinitionEntity;
import org.example.finzin.entity.StreakEntity;
import org.example.finzin.entity.UserAchievementEntity;
import org.example.finzin.entity.UserXpEntity;
import org.example.finzin.repository.AchievementDefinitionRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationQueryServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private XPService xpService;
    @Mock private GamificationSettingsService settingsService;
    @Mock private StreakService streakService;
    @Mock private AchievementDefinitionRepository achievementRepository;
    @Mock private UserAchievementRepository userAchievementRepository;

    private GamificationQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new GamificationQueryService(xpService, settingsService, streakService, achievementRepository, userAchievementRepository);
    }

    private UserXpEntity userXp(long totalXp) {
        UserXpEntity xp = new UserXpEntity();
        xp.setUserId(USER_ID);
        xp.setTotalXp(totalXp);
        xp.setCurrentLevel(XPService.levelFor(totalXp).number());
        return xp;
    }

    private AchievementDefinitionEntity definition(Long id, String category, double threshold) {
        AchievementDefinitionEntity d = new AchievementDefinitionEntity();
        d.setId(id);
        d.setCode("CODE_" + id);
        d.setCategory(category);
        d.setName("Achievement " + id);
        d.setDescription("desc");
        d.setIcon("icon");
        d.setTierColor("gold");
        d.setThreshold(threshold);
        d.setXpReward(10);
        d.setIsMilestone(false);
        d.setActive(true);
        return d;
    }

    private UserAchievementEntity userAchievement(Long achievementId, String status, double progress) {
        UserAchievementEntity ua = new UserAchievementEntity();
        ua.setUserId(USER_ID);
        ua.setAchievementId(achievementId);
        ua.setStatus(status);
        ua.setProgressCurrent(progress);
        return ua;
    }

    // ===================== summary =====================

    @Test
    void summaryAssemblesXpLevelAchievementAndStreakCountsTogether() {
        when(xpService.getOrCreate(USER_ID)).thenReturn(userXp(250));
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(userAchievementRepository.countByUserIdAndStatus(USER_ID, "UNLOCKED")).thenReturn(3L);
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(definition(1L, "SAVINGS", 1), definition(2L, "SAVINGS", 1)));
        StreakEntity streak = new StreakEntity();
        streak.setCurrentStreak(5);
        streak.setLongestStreak(9);
        when(streakService.get(USER_ID, "DAILY_ACTIVE")).thenReturn(streak);

        Map<String, Object> summary = queryService.summary(USER_ID);

        assertEquals(true, summary.get("enabled"));
        assertEquals(250L, summary.get("totalXp"));
        assertEquals(2, summary.get("currentLevel"), "250 XP is level 2 (Budget Beginner)");
        assertEquals(3L, summary.get("achievementsUnlocked"));
        assertEquals(2L, summary.get("achievementsTotal"), "achievementsTotal is a long (findByActiveTrue().size() assigned to a long), not an int");
        assertEquals(5, summary.get("currentStreak"));
        assertEquals(9, summary.get("longestStreak"));
    }

    @Test
    void summaryDefaultsStreaksToZeroWhenTheUserHasNeverHadOne() {
        when(xpService.getOrCreate(USER_ID)).thenReturn(userXp(0));
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(userAchievementRepository.countByUserIdAndStatus(USER_ID, "UNLOCKED")).thenReturn(0L);
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of());
        when(streakService.get(USER_ID, "DAILY_ACTIVE")).thenReturn(null);

        Map<String, Object> summary = queryService.summary(USER_ID);

        assertEquals(0, summary.get("currentStreak"));
        assertEquals(0, summary.get("longestStreak"));
    }

    @Test
    void summaryReportsNullNextLevelInfoAtMaxLevel() {
        when(xpService.getOrCreate(USER_ID)).thenReturn(userXp(15000));
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(userAchievementRepository.countByUserIdAndStatus(USER_ID, "UNLOCKED")).thenReturn(0L);
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of());
        when(streakService.get(USER_ID, "DAILY_ACTIVE")).thenReturn(null);

        Map<String, Object> summary = queryService.summary(USER_ID);

        assertNull(summary.get("nextLevelXp"));
        assertNull(summary.get("nextLevelName"));
    }

    // ===================== achievements =====================

    @Test
    void achievementsFiltersByCategoryCaseInsensitively() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(
                definition(1L, "SAVINGS", 100), definition(2L, "BUDGETING", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<Map<String, Object>> result = queryService.achievements(USER_ID, "savings");

        assertEquals(1, result.size());
        assertEquals("SAVINGS", result.get(0).get("category"));
    }

    @Test
    void achievementsReturnsEveryActiveDefinitionWhenNoCategoryFilterIsGiven() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(
                definition(1L, "SAVINGS", 100), definition(2L, "BUDGETING", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertEquals(2, queryService.achievements(USER_ID, null).size());
    }

    @Test
    void achievementsReportsLockedForAnyDefinitionWithNoUserAchievementRowYet() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(definition(1L, "SAVINGS", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of());

        Map<String, Object> result = queryService.achievements(USER_ID, null).get(0);

        assertEquals("LOCKED", result.get("status"));
        assertEquals(0.0, result.get("progressCurrent"));
    }

    @Test
    void achievementsReportsTheUsersActualStatusAndProgressWhenARowExists() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(definition(1L, "SAVINGS", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of(userAchievement(1L, "UNLOCKED", 100.0)));

        Map<String, Object> result = queryService.achievements(USER_ID, null).get(0);

        assertEquals("UNLOCKED", result.get("status"));
        assertEquals(100.0, result.get("progressCurrent"));
    }

    // ===================== nearestToUnlocking =====================

    @Test
    void nearestToUnlockingExcludesAlreadyUnlockedAchievements() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(definition(1L, "SAVINGS", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of(userAchievement(1L, "UNLOCKED", 100.0)));

        assertEquals(0, queryService.nearestToUnlocking(USER_ID, 5).size());
    }

    @Test
    void nearestToUnlockingSortsByDescendingProgressFractionAndRespectsTheLimit() {
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(
                definition(1L, "A", 100), definition(2L, "B", 100), definition(3L, "C", 100)));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of(
                userAchievement(1L, "LOCKED", 10.0),  // 10%
                userAchievement(2L, "LOCKED", 90.0),  // 90% -- closest
                userAchievement(3L, "LOCKED", 50.0))); // 50%

        List<Map<String, Object>> result = queryService.nearestToUnlocking(USER_ID, 2);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).get("id"), "the achievement closest to its threshold must come first");
        assertEquals(3L, result.get(1).get("id"));
    }

    @Test
    void nearestToUnlockingTreatsAZeroThresholdAsZeroProgressRatherThanDividingByZero() {
        AchievementDefinitionEntity zeroThreshold = definition(1L, "A", 0.0);
        when(achievementRepository.findByActiveTrue()).thenReturn(List.of(zeroThreshold));
        when(userAchievementRepository.findByUserId(USER_ID)).thenReturn(List.of());

        // Must not throw ArithmeticException / return NaN that breaks the comparator.
        List<Map<String, Object>> result = queryService.nearestToUnlocking(USER_ID, 5);
        assertEquals(1, result.size());
    }

    // ===================== toAchievementResponse =====================

    @Test
    void toAchievementResponseMapsEveryDisplayFieldFromTheDefinition() {
        AchievementDefinitionEntity def = definition(1L, "SAVINGS", 500.0);

        Map<String, Object> response = queryService.toAchievementResponse(def, null);

        assertEquals("CODE_1", response.get("code"));
        assertEquals("SAVINGS", response.get("category"));
        assertEquals(500.0, response.get("progressTarget"));
        assertEquals("LOCKED", response.get("status"));
        assertNull(response.get("unlockedAt"));
    }
}
