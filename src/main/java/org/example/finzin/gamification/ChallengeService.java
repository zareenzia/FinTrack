package org.example.finzin.gamification;

import org.example.finzin.entity.ChallengeDefinitionEntity;
import org.example.finzin.entity.UserChallengeEntity;
import org.example.finzin.repository.ChallengeDefinitionRepository;
import org.example.finzin.repository.UserChallengeRepository;
import org.example.finzin.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monthly challenges (V1 scope — weekly/daily deferred, see the plan). Progress is recorded
 * through the exact same {@code metricKey} vocabulary and event call sites already feeding
 * {@link ProgressService}'s lifetime counters (see {@link ProgressService#incrementCounter}) —
 * this class never queries "this month's" data itself, it only accumulates the same deltas the
 * achievement counters already receive, scoped to the current period's row.
 */
@Service
public class ChallengeService {
    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    private final ChallengeDefinitionRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final XPService xpService;
    private final NotificationService notificationService;

    public ChallengeService(ChallengeDefinitionRepository challengeRepository, UserChallengeRepository userChallengeRepository,
                             XPService xpService, NotificationService notificationService) {
        this.challengeRepository = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.xpService = xpService;
        this.notificationService = notificationService;
    }

    public static String currentPeriodKey() {
        return YearMonth.now().toString();
    }

    /**
     * Idempotent: a no-op once this period's rows already exist for the user. Expires any prior
     * period's still-IN_PROGRESS rows (display-only status flip, no penalty) and generates one row
     * per active challenge definition for the new period.
     */
    public void ensureChallengesForCurrentPeriod(Long userId) {
        String periodKey = currentPeriodKey();
        if (userChallengeRepository.existsByUserIdAndPeriodKey(userId, periodKey)) return;

        for (UserChallengeEntity stale : userChallengeRepository.findByUserIdAndStatus(userId, "IN_PROGRESS")) {
            if (!periodKey.equals(stale.getPeriodKey())) {
                stale.setStatus("EXPIRED");
                userChallengeRepository.save(stale);
            }
        }

        for (ChallengeDefinitionEntity def : challengeRepository.findByActiveTrue()) {
            UserChallengeEntity uc = new UserChallengeEntity();
            uc.setUserId(userId);
            uc.setChallengeId(def.getId());
            uc.setPeriodKey(periodKey);
            uc.setProgressCurrent(0.0);
            uc.setTargetValue(def.getTargetValue());
            userChallengeRepository.save(uc);
        }
    }

    /** Called alongside every lifetime counter increment — a no-op for any metricKey with no active challenge. */
    public void recordProgress(Long userId, String metricKey, double delta) {
        List<ChallengeDefinitionEntity> defs = challengeRepository.findByMetricKeyAndActiveTrue(metricKey);
        if (defs.isEmpty()) return;
        String periodKey = currentPeriodKey();

        for (ChallengeDefinitionEntity def : defs) {
            UserChallengeEntity uc = userChallengeRepository.findByUserIdAndChallengeIdAndPeriodKey(userId, def.getId(), periodKey).orElse(null);
            if (uc == null || !"IN_PROGRESS".equals(uc.getStatus())) continue;

            uc.setProgressCurrent(uc.getProgressCurrent() + delta);
            boolean completed = uc.getProgressCurrent() >= uc.getTargetValue();
            if (completed) {
                uc.setStatus("COMPLETED");
                uc.setCompletedAt(LocalDateTime.now());
            }
            userChallengeRepository.save(uc);

            if (completed) {
                xpService.awardXp(userId, def.getXpReward(), "CHALLENGE_COMPLETED", "CHALLENGE", uc.getId().toString());
                notificationService.create(userId, "CHALLENGE_COMPLETED", "🏆 Challenge Completed!",
                        def.getName() + " — " + def.getDescription(), "CHALLENGE", def.getId());
                log.info("Challenge completed userId={} code={} xpReward={}", userId, def.getCode(), def.getXpReward());
            }
        }
    }

    public List<UserChallengeEntity> getCurrentChallenges(Long userId) {
        return userChallengeRepository.findByUserIdAndPeriodKey(userId, currentPeriodKey());
    }

    public void deleteAllForUser(Long userId) {
        userChallengeRepository.deleteByUserId(userId);
    }

    /** The current period's challenges joined with their definition (name/description/xpReward) — shared by the REST endpoint and the AI tool. */
    public List<Map<String, Object>> getCurrentChallengesWithDefinitions(Long userId) {
        Map<Long, ChallengeDefinitionEntity> defsById = challengeRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(ChallengeDefinitionEntity::getId, d -> d));
        return getCurrentChallenges(userId).stream()
                .map(uc -> {
                    ChallengeDefinitionEntity def = defsById.get(uc.getChallengeId());
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", def != null ? def.getCode() : null);
                    map.put("name", def != null ? def.getName() : "Unknown challenge");
                    map.put("description", def != null ? def.getDescription() : null);
                    map.put("xpReward", def != null ? def.getXpReward() : null);
                    map.put("progressCurrent", uc.getProgressCurrent());
                    map.put("targetValue", uc.getTargetValue());
                    map.put("status", uc.getStatus());
                    map.put("periodKey", uc.getPeriodKey());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
