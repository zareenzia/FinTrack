package org.example.finzin.gamification;

import org.example.finzin.entity.ChallengeDefinitionEntity;
import org.example.finzin.entity.UserChallengeEntity;
import org.example.finzin.repository.ChallengeDefinitionRepository;
import org.example.finzin.repository.UserChallengeRepository;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Progress is fed by the exact same delta {@link ProgressService#incrementCounter} already
 * receives for lifetime achievement counters — these tests cover the period-scoped accumulation
 * and completion path that sits on top of that shared call site.
 */
@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long CHALLENGE_ID = 5L;
    private static final String METRIC_KEY = "notes.count";

    @Mock private ChallengeDefinitionRepository challengeRepository;
    @Mock private UserChallengeRepository userChallengeRepository;
    @Mock private XPService xpService;
    @Mock private NotificationService notificationService;

    private ChallengeService challengeService;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(challengeRepository, userChallengeRepository, xpService, notificationService);
    }

    private ChallengeDefinitionEntity definition(double target, int xpReward) {
        ChallengeDefinitionEntity def = new ChallengeDefinitionEntity();
        def.setId(CHALLENGE_ID);
        def.setCode("CH_TEST");
        def.setName("Test Challenge");
        def.setDescription("Test description");
        def.setMetricKey(METRIC_KEY);
        def.setTargetValue(target);
        def.setXpReward(xpReward);
        return def;
    }

    private UserChallengeEntity inProgress(double current, double target) {
        UserChallengeEntity uc = new UserChallengeEntity();
        uc.setId(9L);
        uc.setUserId(USER_ID);
        uc.setChallengeId(CHALLENGE_ID);
        uc.setPeriodKey(ChallengeService.currentPeriodKey());
        uc.setProgressCurrent(current);
        uc.setTargetValue(target);
        uc.setStatus("IN_PROGRESS");
        return uc;
    }

    @Test
    void recordProgressDoesNothingWhenNoChallengeTargetsThatMetric() {
        when(challengeRepository.findByMetricKeyAndActiveTrue(METRIC_KEY)).thenReturn(List.of());

        challengeService.recordProgress(USER_ID, METRIC_KEY, 1.0);

        verify(userChallengeRepository, never()).save(any());
    }

    @Test
    void recordProgressAccumulatesWithoutCompletingWhenBelowTarget() {
        ChallengeDefinitionEntity def = definition(5.0, 15);
        UserChallengeEntity uc = inProgress(2.0, 5.0);
        when(challengeRepository.findByMetricKeyAndActiveTrue(METRIC_KEY)).thenReturn(List.of(def));
        when(userChallengeRepository.findByUserIdAndChallengeIdAndPeriodKey(USER_ID, CHALLENGE_ID, ChallengeService.currentPeriodKey()))
                .thenReturn(Optional.of(uc));
        when(userChallengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        challengeService.recordProgress(USER_ID, METRIC_KEY, 1.0);

        assertEquals(3.0, uc.getProgressCurrent());
        assertEquals("IN_PROGRESS", uc.getStatus());
        verify(xpService, never()).awardXp(anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void recordProgressCompletesAndAwardsXpWhenTargetReached() {
        ChallengeDefinitionEntity def = definition(5.0, 15);
        UserChallengeEntity uc = inProgress(4.0, 5.0);
        when(challengeRepository.findByMetricKeyAndActiveTrue(METRIC_KEY)).thenReturn(List.of(def));
        when(userChallengeRepository.findByUserIdAndChallengeIdAndPeriodKey(USER_ID, CHALLENGE_ID, ChallengeService.currentPeriodKey()))
                .thenReturn(Optional.of(uc));
        when(userChallengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        challengeService.recordProgress(USER_ID, METRIC_KEY, 1.0);

        assertEquals(5.0, uc.getProgressCurrent());
        assertEquals("COMPLETED", uc.getStatus());
        verify(xpService).awardXp(eq(USER_ID), eq(15), eq("CHALLENGE_COMPLETED"), eq("CHALLENGE"), eq("9"));
        verify(notificationService).create(eq(USER_ID), eq("CHALLENGE_COMPLETED"), anyString(), anyString(), eq("CHALLENGE"), eq(CHALLENGE_ID));
    }

    @Test
    void recordProgressIgnoresAlreadyCompletedChallenges() {
        ChallengeDefinitionEntity def = definition(5.0, 15);
        UserChallengeEntity uc = inProgress(5.0, 5.0);
        uc.setStatus("COMPLETED");
        when(challengeRepository.findByMetricKeyAndActiveTrue(METRIC_KEY)).thenReturn(List.of(def));
        when(userChallengeRepository.findByUserIdAndChallengeIdAndPeriodKey(USER_ID, CHALLENGE_ID, ChallengeService.currentPeriodKey()))
                .thenReturn(Optional.of(uc));

        challengeService.recordProgress(USER_ID, METRIC_KEY, 1.0);

        verify(userChallengeRepository, never()).save(any());
        verify(xpService, never()).awardXp(anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void ensureChallengesForCurrentPeriodIsANoOpWhenAlreadyGenerated() {
        when(userChallengeRepository.existsByUserIdAndPeriodKey(USER_ID, ChallengeService.currentPeriodKey())).thenReturn(true);

        challengeService.ensureChallengesForCurrentPeriod(USER_ID);

        verify(challengeRepository, never()).findByActiveTrue();
        verify(userChallengeRepository, never()).save(any());
    }

    @Test
    void ensureChallengesForCurrentPeriodGeneratesOneRowPerActiveDefinitionAndExpiresStaleOnes() {
        String currentPeriod = ChallengeService.currentPeriodKey();
        String priorPeriod = YearMonth.parse(currentPeriod).minusMonths(1).toString();
        when(userChallengeRepository.existsByUserIdAndPeriodKey(USER_ID, currentPeriod)).thenReturn(false);

        UserChallengeEntity staleFromLastMonth = inProgress(2.0, 5.0);
        staleFromLastMonth.setPeriodKey(priorPeriod);
        when(userChallengeRepository.findByUserIdAndStatus(USER_ID, "IN_PROGRESS")).thenReturn(List.of(staleFromLastMonth));
        when(userChallengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChallengeDefinitionEntity def1 = definition(5.0, 15);
        ChallengeDefinitionEntity def2 = definition(2000.0, 40);
        def2.setId(6L);
        when(challengeRepository.findByActiveTrue()).thenReturn(List.of(def1, def2));

        challengeService.ensureChallengesForCurrentPeriod(USER_ID);

        assertEquals("EXPIRED", staleFromLastMonth.getStatus());
        verify(userChallengeRepository, times(3)).save(any()); // 1 expiry + 2 new rows
    }
}
