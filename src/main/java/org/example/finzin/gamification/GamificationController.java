package org.example.finzin.gamification;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.GamificationSettingsEntity;
import org.example.finzin.entity.XpHistoryEntity;
import org.example.finzin.repository.StreakRepository;
import org.example.finzin.repository.UserAchievementRepository;
import org.example.finzin.repository.UserStatCounterRepository;
import org.example.finzin.repository.UserXpRepository;
import org.example.finzin.repository.XpHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationSettingsService settingsService;
    private final GamificationQueryService queryService;
    private final ChallengeService challengeService;
    private final UserAchievementRepository userAchievementRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final UserXpRepository userXpRepository;
    private final StreakRepository streakRepository;
    private final UserStatCounterRepository userStatCounterRepository;

    public GamificationController(GamificationSettingsService settingsService,
                                   GamificationQueryService queryService, ChallengeService challengeService,
                                   UserAchievementRepository userAchievementRepository, XpHistoryRepository xpHistoryRepository,
                                   UserXpRepository userXpRepository, StreakRepository streakRepository,
                                   UserStatCounterRepository userStatCounterRepository) {
        this.settingsService = settingsService;
        this.queryService = queryService;
        this.challengeService = challengeService;
        this.userAchievementRepository = userAchievementRepository;
        this.xpHistoryRepository = xpHistoryRepository;
        this.userXpRepository = userXpRepository;
        this.streakRepository = streakRepository;
        this.userStatCounterRepository = userStatCounterRepository;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest request) {
        return queryService.summary(getUserId(request));
    }

    @GetMapping("/levels")
    public List<Map<String, Object>> levels() {
        return XPService.levels().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", l.number());
            m.put("name", l.name());
            m.put("xpRequired", l.xpRequired());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/achievements")
    public List<Map<String, Object>> achievements(HttpServletRequest request, @RequestParam(required = false) String category) {
        return queryService.achievements(getUserId(request), category);
    }

    @GetMapping("/challenges")
    public List<Map<String, Object>> challenges(HttpServletRequest request) {
        Long userId = getUserId(request);
        challengeService.ensureChallengesForCurrentPeriod(userId);
        return challengeService.getCurrentChallengesWithDefinitions(userId);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(HttpServletRequest request) {
        Long userId = getUserId(request);
        return xpHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(HttpServletRequest request) {
        return toSettingsResponse(settingsService.getOrDefault(getUserId(request)));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(HttpServletRequest request, @RequestBody SettingsRequest body) {
        Long userId = getUserId(request);
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        GamificationSettingsEntity updated = settingsService.update(userId, body.enabled(), body.enableNotifications(),
                body.showDashboardWidget(), body.enableCelebrations(), body.enableChallenges(),
                body.enableStreakTracking(), body.showXp());
        return ResponseEntity.ok(toSettingsResponse(updated));
    }

    @DeleteMapping("/reset")
    @Transactional
    public ResponseEntity<?> resetProgress(HttpServletRequest request) {
        Long userId = getUserId(request);
        xpHistoryRepository.deleteByUserId(userId);
        userXpRepository.deleteByUserId(userId);
        userAchievementRepository.deleteByUserId(userId);
        streakRepository.deleteByUserId(userId);
        userStatCounterRepository.deleteByUserId(userId);
        challengeService.deleteAllForUser(userId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toHistoryResponse(XpHistoryEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("amount", e.getAmount());
        map.put("reason", e.getReason());
        map.put("sourceType", e.getSourceType());
        map.put("createdAt", e.getCreatedAt());
        return map;
    }

    private Map<String, Object> toSettingsResponse(GamificationSettingsEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", e.getEnabled());
        map.put("enableNotifications", e.getEnableNotifications());
        map.put("showDashboardWidget", e.getShowDashboardWidget());
        map.put("enableCelebrations", e.getEnableCelebrations());
        map.put("enableChallenges", e.getEnableChallenges());
        map.put("enableStreakTracking", e.getEnableStreakTracking());
        map.put("showXp", e.getShowXp());
        return map;
    }

    private record SettingsRequest(Boolean enabled, Boolean enableNotifications, Boolean showDashboardWidget,
                                    Boolean enableCelebrations, Boolean enableChallenges,
                                    Boolean enableStreakTracking, Boolean showXp) {
    }
}
