package org.example.finzin.gamification;

import org.example.finzin.entity.UserXpEntity;
import org.example.finzin.entity.XpHistoryEntity;
import org.example.finzin.repository.UserXpRepository;
import org.example.finzin.repository.XpHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Awards XP idempotently and tracks level. Every award is dedup'd on
 * {@code (userId, sourceType, sourceId, reason)} — checked before inserting rather than
 * insert-then-catch-constraint-violation, since this app's usage is low-concurrency enough that a
 * plain existence check is safe and sidesteps Hibernate's "session left unusable after a caught
 * flush exception" pitfall entirely. The DB unique constraint (see {@code XpHistoryEntity}) is
 * kept as a defensive backstop for the rare concurrent-request race, not the primary mechanism.
 */
@Service
public class XPService {
    private static final Logger log = LoggerFactory.getLogger(XPService.class);

    /** Fixed, rarely-changing progression — not a DB table, unlike the dozens of achievements. */
    public record Level(int number, String name, long xpRequired) {
    }

    private static final List<Level> LEVELS = List.of(
            new Level(1, "Starter", 0),
            new Level(2, "Budget Beginner", 200),
            new Level(3, "Money Manager", 600),
            new Level(4, "Smart Saver", 1200),
            new Level(5, "Financial Planner", 2000),
            new Level(6, "Investment Explorer", 3200),
            new Level(7, "Money Strategist", 5000),
            new Level(8, "Financial Expert", 7500),
            new Level(9, "Wealth Builder", 11000),
            new Level(10, "Financial Master", 15000)
    );

    private final UserXpRepository userXpRepository;
    private final XpHistoryRepository xpHistoryRepository;

    public XPService(UserXpRepository userXpRepository, XpHistoryRepository xpHistoryRepository) {
        this.userXpRepository = userXpRepository;
        this.xpHistoryRepository = xpHistoryRepository;
    }

    public static List<Level> levels() {
        return LEVELS;
    }

    public static Level levelFor(long totalXp) {
        Level current = LEVELS.get(0);
        for (Level level : LEVELS) {
            if (totalXp >= level.xpRequired()) current = level;
            else break;
        }
        return current;
    }

    /** Null once the max level has been reached. */
    public static Level nextLevel(long totalXp) {
        Level current = levelFor(totalXp);
        return LEVELS.stream().filter(l -> l.number() == current.number() + 1).findFirst().orElse(null);
    }

    public UserXpEntity getOrCreate(Long userId) {
        return userXpRepository.findByUserId(userId).orElseGet(() -> {
            UserXpEntity entity = new UserXpEntity();
            entity.setUserId(userId);
            return userXpRepository.save(entity);
        });
    }

    /**
     * Returns true only if XP was actually newly awarded (false means this exact source already
     * awarded this exact reason before — a safe, expected no-op, not an error).
     */
    public boolean awardXp(Long userId, int amount, String reason, String sourceType, String sourceId) {
        if (xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceIdAndReason(userId, sourceType, sourceId, reason)) {
            return false;
        }
        XpHistoryEntity history = new XpHistoryEntity();
        history.setUserId(userId);
        history.setAmount(amount);
        history.setReason(reason);
        history.setSourceType(sourceType);
        history.setSourceId(sourceId);
        try {
            xpHistoryRepository.save(history);
        } catch (DataIntegrityViolationException e) {
            // Defensive backstop for the rare concurrent-request race the existence check above
            // doesn't fully close — already awarded by a racing request, treat as a no-op.
            log.debug("XP award race for userId={} sourceType={} sourceId={} reason={} — already recorded", userId, sourceType, sourceId, reason);
            return false;
        }

        UserXpEntity userXp = getOrCreate(userId);
        long newTotal = userXp.getTotalXp() + amount;
        userXp.setTotalXp(newTotal);
        userXp.setCurrentLevel(levelFor(newTotal).number());
        userXpRepository.save(userXp);
        return true;
    }
}
