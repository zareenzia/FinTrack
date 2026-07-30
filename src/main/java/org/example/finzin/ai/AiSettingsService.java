package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.repository.AiSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class AiSettingsService {
    private final AiSettingsRepository repository;

    public AiSettingsService(AiSettingsRepository repository) {
        this.repository = repository;
    }

    public AiSettingsEntity getOrDefault(Long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            AiSettingsEntity entity = new AiSettingsEntity();
            entity.setUserId(userId);
            return repository.save(entity);
        });
    }

    /** Returns null if maxTokens/temperature are out of range — caller responds 400. */
    public AiSettingsEntity update(Long userId, String model, Integer maxTokens, Double temperature, Boolean enabled, Boolean developerMode,
                                    Boolean enableProactiveInsights, Boolean enableBudgetCoaching, Boolean enableSavingsCoaching,
                                    Boolean enableMonthlyReports, Boolean enableDashboardSummary) {
        if (maxTokens != null && (maxTokens < 100 || maxTokens > 4000)) return null;
        if (temperature != null && (temperature < 0 || temperature > 2)) return null;

        AiSettingsEntity entity = getOrDefault(userId);
        if (model != null && !model.isBlank()) entity.setModel(model.trim());
        if (maxTokens != null) entity.setMaxTokens(maxTokens);
        if (temperature != null) entity.setTemperature(temperature);
        if (enabled != null) entity.setEnabled(enabled);
        if (developerMode != null) entity.setDeveloperMode(developerMode);
        if (enableProactiveInsights != null) entity.setEnableProactiveInsights(enableProactiveInsights);
        if (enableBudgetCoaching != null) entity.setEnableBudgetCoaching(enableBudgetCoaching);
        if (enableSavingsCoaching != null) entity.setEnableSavingsCoaching(enableSavingsCoaching);
        if (enableMonthlyReports != null) entity.setEnableMonthlyReports(enableMonthlyReports);
        if (enableDashboardSummary != null) entity.setEnableDashboardSummary(enableDashboardSummary);
        return repository.save(entity);
    }
}
