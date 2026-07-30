package org.example.finzin.gamification;

import org.example.finzin.entity.AchievementDefinitionEntity;
import org.example.finzin.entity.StreakEntity;
import org.example.finzin.entity.UserAchievementEntity;
import org.example.finzin.entity.UserXpEntity;
import org.example.finzin.repository.AchievementDefinitionRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-side aggregation shared by {@link GamificationController} (REST) and
 * {@link org.example.finzin.ai.FinancialToolExecutor} (AI chat tools) — one place computing
 * "where does this user stand right now" so the two callers can never drift apart.
 */
@Service
public class GamificationQueryService {

    private final XPService xpService;
    private final GamificationSettingsService settingsService;
    private final StreakService streakService;
    private final AchievementDefinitionRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    public GamificationQueryService(XPService xpService, GamificationSettingsService settingsService,
                                     StreakService streakService, AchievementDefinitionRepository achievementRepository,
                                     UserAchievementRepository userAchievementRepository) {
        this.xpService = xpService;
        this.settingsService = settingsService;
        this.streakService = streakService;
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
    }

    public Map<String, Object> summary(Long userId) {
        UserXpEntity userXp = xpService.getOrCreate(userId);
        XPService.Level currentLevel = XPService.levelFor(userXp.getTotalXp());
        XPService.Level nextLevel = XPService.nextLevel(userXp.getTotalXp());
        long unlocked = userAchievementRepository.countByUserIdAndStatus(userId, "UNLOCKED");
        long total = achievementRepository.findByActiveTrue().size();
        StreakEntity dailyStreak = streakService.get(userId, "DAILY_ACTIVE");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", settingsService.isEnabled(userId));
        map.put("totalXp", userXp.getTotalXp());
        map.put("currentLevel", currentLevel.number());
        map.put("currentLevelName", currentLevel.name());
        map.put("currentLevelXp", currentLevel.xpRequired());
        map.put("nextLevelXp", nextLevel != null ? nextLevel.xpRequired() : null);
        map.put("nextLevelName", nextLevel != null ? nextLevel.name() : null);
        map.put("achievementsUnlocked", unlocked);
        map.put("achievementsTotal", total);
        map.put("currentStreak", dailyStreak != null && dailyStreak.getCurrentStreak() != null ? dailyStreak.getCurrentStreak() : 0);
        map.put("longestStreak", dailyStreak != null && dailyStreak.getLongestStreak() != null ? dailyStreak.getLongestStreak() : 0);
        return map;
    }

    public List<Map<String, Object>> achievements(Long userId, String category) {
        Map<Long, UserAchievementEntity> userMap = userAchievementRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserAchievementEntity::getAchievementId, ua -> ua));
        return achievementRepository.findByActiveTrue().stream()
                .filter(d -> category == null || category.equalsIgnoreCase(d.getCategory()))
                .map(d -> toAchievementResponse(d, userMap.get(d.getId())))
                .collect(Collectors.toList());
    }

    /** Locked achievements closest to their threshold, nearest-first — backs the "what am I close to unlocking?" AI tool. */
    public List<Map<String, Object>> nearestToUnlocking(Long userId, int limit) {
        Map<Long, UserAchievementEntity> userMap = userAchievementRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserAchievementEntity::getAchievementId, ua -> ua));
        return achievementRepository.findByActiveTrue().stream()
                .filter(d -> !"UNLOCKED".equals(statusOf(userMap.get(d.getId()))))
                .sorted(Comparator.comparingDouble((AchievementDefinitionEntity d) -> -progressFraction(d, userMap.get(d.getId()))))
                .limit(limit)
                .map(d -> toAchievementResponse(d, userMap.get(d.getId())))
                .collect(Collectors.toList());
    }

    private String statusOf(UserAchievementEntity ua) {
        return ua != null ? ua.getStatus() : "LOCKED";
    }

    private double progressFraction(AchievementDefinitionEntity def, UserAchievementEntity ua) {
        double target = def.getThreshold() != null ? def.getThreshold() : 0.0;
        if (target <= 0) return 0.0;
        double current = ua != null && ua.getProgressCurrent() != null ? ua.getProgressCurrent() : 0.0;
        return current / target;
    }

    Map<String, Object> toAchievementResponse(AchievementDefinitionEntity def, UserAchievementEntity userAchievement) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", def.getId());
        map.put("code", def.getCode());
        map.put("category", def.getCategory());
        map.put("name", def.getName());
        map.put("description", def.getDescription());
        map.put("icon", def.getIcon());
        map.put("tierColor", def.getTierColor());
        map.put("xpReward", def.getXpReward());
        map.put("isMilestone", def.getIsMilestone());
        map.put("status", userAchievement != null ? userAchievement.getStatus() : "LOCKED");
        map.put("progressCurrent", userAchievement != null ? userAchievement.getProgressCurrent() : 0.0);
        map.put("progressTarget", def.getThreshold());
        map.put("unlockedAt", userAchievement != null ? userAchievement.getUnlockedAt() : null);
        return map;
    }
}
