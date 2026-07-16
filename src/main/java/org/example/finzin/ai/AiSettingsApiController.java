package org.example.finzin.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.AiSettingsEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/settings")
public class AiSettingsApiController {
    private final AiSettingsService aiSettingsService;

    public AiSettingsApiController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public Map<String, Object> getSettings(HttpServletRequest request) {
        Long userId = getUserId(request);
        return toResponse(aiSettingsService.getOrDefault(userId));
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(HttpServletRequest request, @RequestBody SettingsRequest body) {
        Long userId = getUserId(request);
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        AiSettingsEntity updated = aiSettingsService.update(userId, body.model(), body.maxTokens(), body.temperature(), body.enabled(), body.developerMode(),
                body.enableProactiveInsights(), body.enableBudgetCoaching(), body.enableSavingsCoaching(), body.enableMonthlyReports(), body.enableDashboardSummary());
        if (updated == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "maxTokens must be 100-4000 and temperature must be 0-2"));
        }
        return ResponseEntity.ok(toResponse(updated));
    }

    private Map<String, Object> toResponse(AiSettingsEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider", entity.getProvider());
        map.put("model", entity.getModel());
        map.put("maxTokens", entity.getMaxTokens());
        map.put("temperature", entity.getTemperature());
        map.put("enabled", entity.getEnabled());
        map.put("developerMode", entity.getDeveloperMode());
        map.put("enableProactiveInsights", entity.getEnableProactiveInsights());
        map.put("enableBudgetCoaching", entity.getEnableBudgetCoaching());
        map.put("enableSavingsCoaching", entity.getEnableSavingsCoaching());
        map.put("enableMonthlyReports", entity.getEnableMonthlyReports());
        map.put("enableDashboardSummary", entity.getEnableDashboardSummary());
        return map;
    }

    private record SettingsRequest(String model, Integer maxTokens, Double temperature, Boolean enabled, Boolean developerMode,
                                    Boolean enableProactiveInsights, Boolean enableBudgetCoaching, Boolean enableSavingsCoaching,
                                    Boolean enableMonthlyReports, Boolean enableDashboardSummary) {}
}
