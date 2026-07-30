package org.example.finzin.gamification;

import org.example.finzin.ai.FinancialHealthService;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily backstop for the achievement metrics that aren't naturally event-driven, plus monthly
 * challenge rotation. Stage 1 shipped only the net-worth-snapshot freshening pass (needed since
 * growth achievements can't be backfilled after the fact); Stage 2 adds the actual re-checks for
 * the two metrics that depended on that snapshot history, plus generating each user's current
 * month's challenges.
 */
@Component
public class GamificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(GamificationScheduler.class);

    private final UserRepository userRepository;
    private final GamificationSettingsService settingsService;
    private final FinancialHealthService financialHealthService;
    private final AchievementEngine achievementEngine;
    private final ChallengeService challengeService;

    public GamificationScheduler(UserRepository userRepository, GamificationSettingsService settingsService,
                                  FinancialHealthService financialHealthService, AchievementEngine achievementEngine,
                                  ChallengeService challengeService) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.financialHealthService = financialHealthService;
        this.achievementEngine = achievementEngine;
        this.challengeService = challengeService;
    }

    @Scheduled(cron = "0 15 0 * * *")
    public void runDailySnapshotFreshening() {
        refreshAllEnabledUsers();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(this::refreshAllEnabledUsers, "gamification-snapshot-init-sync").start();
    }

    private void refreshAllEnabledUsers() {
        for (UserEntity user : userRepository.findAll()) {
            Long userId = user.getId();
            try {
                if (!settingsService.isEnabled(userId)) continue;
                financialHealthService.calculate(userId);
                achievementEngine.checkMetric(userId, "networth.growth_percent");
                achievementEngine.checkMetric(userId, "budget.consecutive_months_within");
                challengeService.ensureChallengesForCurrentPeriod(userId);
            } catch (Exception e) {
                log.warn("Gamification daily refresh failed userId={} errorType={}", userId, e.getClass().getSimpleName());
            }
        }
    }
}
