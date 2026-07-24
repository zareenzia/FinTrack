package org.example.finzin.gamification;

import org.example.finzin.entity.StreakEntity;
import org.example.finzin.repository.StreakRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StreakService {
    private final StreakRepository streakRepository;

    public StreakService(StreakRepository streakRepository) {
        this.streakRepository = streakRepository;
    }

    /**
     * Records one day of activity for {@code streakType}, idempotent per calendar day (calling
     * this twice on the same day is a no-op). A consecutive day increments the streak; a gap
     * resets it to 1; the very first recorded day also starts it at 1.
     */
    public StreakEntity recordActivity(Long userId, String streakType, LocalDate today) {
        StreakEntity streak = streakRepository.findByUserIdAndStreakType(userId, streakType).orElseGet(() -> {
            StreakEntity entity = new StreakEntity();
            entity.setUserId(userId);
            entity.setStreakType(streakType);
            entity.setCurrentStreak(0);
            entity.setLongestStreak(0);
            return entity;
        });

        LocalDate last = streak.getLastActivityDate();
        if (today.equals(last)) {
            return streak; // already recorded today, no-op
        }
        int previousCurrent = streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0;
        int previousLongest = streak.getLongestStreak() != null ? streak.getLongestStreak() : 0;
        int newCurrent = (last != null && last.plusDays(1).equals(today)) ? previousCurrent + 1 : 1;

        streak.setCurrentStreak(newCurrent);
        streak.setLongestStreak(Math.max(previousLongest, newCurrent));
        streak.setLastActivityDate(today);
        return streakRepository.save(streak);
    }

    public StreakEntity get(Long userId, String streakType) {
        return streakRepository.findByUserIdAndStreakType(userId, streakType).orElse(null);
    }

    public List<StreakEntity> getAll(Long userId) {
        return streakRepository.findByUserId(userId);
    }
}
