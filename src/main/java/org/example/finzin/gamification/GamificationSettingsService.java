package org.example.finzin.gamification;

import org.example.finzin.entity.GamificationSettingsEntity;
import org.example.finzin.repository.GamificationSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class GamificationSettingsService {
    private final GamificationSettingsRepository repository;

    public GamificationSettingsService(GamificationSettingsRepository repository) {
        this.repository = repository;
    }

    public GamificationSettingsEntity getOrDefault(Long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            GamificationSettingsEntity entity = new GamificationSettingsEntity();
            entity.setUserId(userId);
            return repository.save(entity);
        });
    }

    public boolean isEnabled(Long userId) {
        return Boolean.TRUE.equals(getOrDefault(userId).getEnabled());
    }

    public GamificationSettingsEntity update(Long userId, Boolean enabled, Boolean enableNotifications,
                                              Boolean showDashboardWidget, Boolean enableCelebrations,
                                              Boolean enableChallenges, Boolean enableStreakTracking, Boolean showXp) {
        GamificationSettingsEntity entity = getOrDefault(userId);
        if (enabled != null) entity.setEnabled(enabled);
        if (enableNotifications != null) entity.setEnableNotifications(enableNotifications);
        if (showDashboardWidget != null) entity.setShowDashboardWidget(showDashboardWidget);
        if (enableCelebrations != null) entity.setEnableCelebrations(enableCelebrations);
        if (enableChallenges != null) entity.setEnableChallenges(enableChallenges);
        if (enableStreakTracking != null) entity.setEnableStreakTracking(enableStreakTracking);
        if (showXp != null) entity.setShowXp(showXp);
        return repository.save(entity);
    }
}
