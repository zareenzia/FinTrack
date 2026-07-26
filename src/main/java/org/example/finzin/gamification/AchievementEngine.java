package org.example.finzin.gamification;

import org.example.finzin.entity.AchievementDefinitionEntity;
import org.example.finzin.entity.UserAchievementEntity;
import org.example.finzin.repository.AchievementDefinitionRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.example.finzin.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-checks every active achievement definition tied to a given metric key against the user's
 * current value for that metric, unlocking + awarding XP + notifying on any newly-crossed
 * threshold. Never re-processes an already-unlocked achievement (checked first, before touching
 * anything else) — that alone makes this safe to call repeatedly for the same metric.
 */
@Service
public class AchievementEngine {
    private static final Logger log = LoggerFactory.getLogger(AchievementEngine.class);

    private final AchievementDefinitionRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final ProgressService progressService;
    private final XPService xpService;
    private final NotificationService notificationService;

    public AchievementEngine(AchievementDefinitionRepository achievementRepository,
                              UserAchievementRepository userAchievementRepository,
                              ProgressService progressService, XPService xpService,
                              NotificationService notificationService) {
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.progressService = progressService;
        this.xpService = xpService;
        this.notificationService = notificationService;
    }

    public void checkMetric(Long userId, String metricKey) {
        List<AchievementDefinitionEntity> candidates = achievementRepository.findByMetricKeyAndActiveTrue(metricKey);
        if (candidates.isEmpty()) return;
        double currentValue = progressService.resolveMetric(userId, metricKey);
        for (AchievementDefinitionEntity def : candidates) {
            evaluate(userId, def, currentValue);
        }
    }

    private void evaluate(Long userId, AchievementDefinitionEntity def, double currentValue) {
        UserAchievementEntity userAchievement = userAchievementRepository.findByUserIdAndAchievementId(userId, def.getId())
                .orElseGet(() -> {
                    UserAchievementEntity entity = new UserAchievementEntity();
                    entity.setUserId(userId);
                    entity.setAchievementId(def.getId());
                    return entity;
                });
        if ("UNLOCKED".equals(userAchievement.getStatus())) {
            return;
        }

        userAchievement.setProgressCurrent(Math.min(currentValue, def.getThreshold()));
        userAchievement.setProgressTarget(def.getThreshold());

        boolean crossed = currentValue >= def.getThreshold();
        if (crossed) {
            userAchievement.setStatus("UNLOCKED");
            userAchievement.setUnlockedAt(LocalDateTime.now());
        }
        userAchievementRepository.save(userAchievement);

        if (crossed) {
            xpService.awardXp(userId, def.getXpReward(), "ACHIEVEMENT_UNLOCKED", "ACHIEVEMENT", String.valueOf(def.getId()));
            boolean milestone = Boolean.TRUE.equals(def.getIsMilestone());
            String notifType = milestone ? "MILESTONE" : "ACHIEVEMENT_UNLOCKED";
            String title = milestone ? "🎉 Milestone Reached!" : "🏆 Achievement Unlocked!";
            notificationService.create(userId, notifType, title,
                    def.getName() + " — " + def.getDescription(), "ACHIEVEMENT", def.getId());
            log.info("Achievement unlocked userId={} code={} xpReward={}", userId, def.getCode(), def.getXpReward());
        }
    }
}
