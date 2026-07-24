package org.example.finzin.gamification;

import org.example.finzin.entity.AchievementDefinitionEntity;
import org.example.finzin.entity.UserAchievementEntity;
import org.example.finzin.repository.AchievementDefinitionRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for the achievement-unlock path: the "never re-process an already-unlocked
 * achievement" guard is what makes it safe for GamificationEventListener to call checkMetric on every
 * matching event, so that's covered explicitly alongside the threshold-crossing and milestone-vs-plain
 * notification branching.
 */
@ExtendWith(MockitoExtension.class)
class AchievementEngineTest {

    private static final Long USER_ID = 42L;
    private static final Long ACHIEVEMENT_ID = 7L;

    @Mock private AchievementDefinitionRepository achievementRepository;
    @Mock private UserAchievementRepository userAchievementRepository;
    @Mock private ProgressService progressService;
    @Mock private XPService xpService;
    @Mock private NotificationService notificationService;

    private AchievementEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AchievementEngine(achievementRepository, userAchievementRepository, progressService, xpService, notificationService);
    }

    private AchievementDefinitionEntity definition(String code, double threshold, int xpReward, boolean milestone) {
        AchievementDefinitionEntity def = new AchievementDefinitionEntity();
        def.setId(ACHIEVEMENT_ID);
        def.setCode(code);
        def.setName("Test Achievement");
        def.setDescription("Test description");
        def.setMetricKey("transactions.count");
        def.setThreshold(threshold);
        def.setXpReward(xpReward);
        def.setIsMilestone(milestone);
        def.setActive(true);
        return def;
    }

    @Test
    void checkMetricDoesNothingWhenNoAchievementsTargetThatMetric() {
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of());

        engine.checkMetric(USER_ID, "transactions.count");

        verify(progressService, never()).resolveMetric(any(), anyString());
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void thresholdNotYetReachedUpdatesProgressButStaysLocked() {
        AchievementDefinitionEntity def = definition("TXN_100", 100, 50, false);
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of(def));
        when(progressService.resolveMetric(USER_ID, "transactions.count")).thenReturn(40.0);
        when(userAchievementRepository.findByUserIdAndAchievementId(USER_ID, ACHIEVEMENT_ID)).thenReturn(Optional.empty());
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.checkMetric(USER_ID, "transactions.count");

        ArgumentCaptor<UserAchievementEntity> captor = ArgumentCaptor.forClass(UserAchievementEntity.class);
        verify(userAchievementRepository).save(captor.capture());
        assertEquals(40.0, captor.getValue().getProgressCurrent());
        assertNull(captor.getValue().getStatus(), "unlock status is only ever set to UNLOCKED, never assigned LOCKED explicitly here");
        verify(xpService, never()).awardXp(any(), anyInt(), anyString(), anyString(), anyString());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void crossingTheThresholdUnlocksAwardsXpAndSendsAPlainAchievementNotification() {
        AchievementDefinitionEntity def = definition("TXN_100", 100, 50, false);
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of(def));
        when(progressService.resolveMetric(USER_ID, "transactions.count")).thenReturn(150.0);
        when(userAchievementRepository.findByUserIdAndAchievementId(USER_ID, ACHIEVEMENT_ID)).thenReturn(Optional.empty());
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.checkMetric(USER_ID, "transactions.count");

        ArgumentCaptor<UserAchievementEntity> captor = ArgumentCaptor.forClass(UserAchievementEntity.class);
        verify(userAchievementRepository).save(captor.capture());
        assertEquals("UNLOCKED", captor.getValue().getStatus());
        assertEquals(100.0, captor.getValue().getProgressCurrent(), "progress caps at the threshold, doesn't overshoot with the raw metric value");
        verify(xpService).awardXp(USER_ID, 50, "ACHIEVEMENT_UNLOCKED", "ACHIEVEMENT", String.valueOf(ACHIEVEMENT_ID));
        verify(notificationService).create(eq(USER_ID), eq("ACHIEVEMENT_UNLOCKED"), anyString(), anyString(), eq("ACHIEVEMENT"), eq(ACHIEVEMENT_ID));
    }

    @Test
    void milestoneAchievementUsesTheMilestoneNotificationTypeInstead() {
        AchievementDefinitionEntity def = definition("TXN_FIRST", 1, 10, true);
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of(def));
        when(progressService.resolveMetric(USER_ID, "transactions.count")).thenReturn(1.0);
        when(userAchievementRepository.findByUserIdAndAchievementId(USER_ID, ACHIEVEMENT_ID)).thenReturn(Optional.empty());
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.checkMetric(USER_ID, "transactions.count");

        verify(notificationService).create(eq(USER_ID), eq("MILESTONE"), anyString(), anyString(), eq("ACHIEVEMENT"), eq(ACHIEVEMENT_ID));
    }

    @Test
    void alreadyUnlockedAchievementIsNeverReprocessed() {
        AchievementDefinitionEntity def = definition("TXN_100", 100, 50, false);
        UserAchievementEntity alreadyUnlocked = new UserAchievementEntity();
        alreadyUnlocked.setUserId(USER_ID);
        alreadyUnlocked.setAchievementId(ACHIEVEMENT_ID);
        alreadyUnlocked.setStatus("UNLOCKED");
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of(def));
        when(progressService.resolveMetric(USER_ID, "transactions.count")).thenReturn(500.0);
        when(userAchievementRepository.findByUserIdAndAchievementId(USER_ID, ACHIEVEMENT_ID)).thenReturn(Optional.of(alreadyUnlocked));

        engine.checkMetric(USER_ID, "transactions.count");

        verify(userAchievementRepository, never()).save(any());
        verify(xpService, never()).awardXp(any(), anyInt(), anyString(), anyString(), anyString());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void multipleAchievementsOnTheSameMetricAreEvaluatedIndependently() {
        AchievementDefinitionEntity def1 = definition("TXN_100", 100, 50, false);
        AchievementDefinitionEntity def2 = definition("TXN_500", 500, 100, false);
        def2.setId(8L);
        when(achievementRepository.findByMetricKeyAndActiveTrue("transactions.count")).thenReturn(List.of(def1, def2));
        when(progressService.resolveMetric(USER_ID, "transactions.count")).thenReturn(150.0);
        when(userAchievementRepository.findByUserIdAndAchievementId(eq(USER_ID), any())).thenReturn(Optional.empty());
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.checkMetric(USER_ID, "transactions.count");

        verify(xpService, times(1)).awardXp(USER_ID, 50, "ACHIEVEMENT_UNLOCKED", "ACHIEVEMENT", "7");
        verify(xpService, never()).awardXp(USER_ID, 100, "ACHIEVEMENT_UNLOCKED", "ACHIEVEMENT", "8");
        verify(progressService, times(1)).resolveMetric(USER_ID, "transactions.count");
    }
}
