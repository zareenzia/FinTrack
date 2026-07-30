package org.example.finzin.gamification;

import org.example.finzin.entity.StreakEntity;
import org.example.finzin.repository.StreakRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    private static final Long USER_ID = 42L;
    private static final String TYPE = "DAILY_ACTIVE";

    @Mock private StreakRepository streakRepository;

    private StreakService streakService;

    @BeforeEach
    void setUp() {
        streakService = new StreakService(streakRepository);
        org.mockito.Mockito.lenient().when(streakRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private StreakEntity streak(int current, int longest, LocalDate lastActivity) {
        StreakEntity s = new StreakEntity();
        s.setUserId(USER_ID);
        s.setStreakType(TYPE);
        s.setCurrentStreak(current);
        s.setLongestStreak(longest);
        s.setLastActivityDate(lastActivity);
        return s;
    }

    @Test
    void firstEverActivityStartsStreakAtOne() {
        when(streakRepository.findByUserIdAndStreakType(USER_ID, TYPE)).thenReturn(Optional.empty());

        StreakEntity result = streakService.recordActivity(USER_ID, TYPE, LocalDate.of(2026, 7, 24));

        assertEquals(1, result.getCurrentStreak());
        assertEquals(1, result.getLongestStreak());
    }

    @Test
    void consecutiveDayIncrementsCurrentStreak() {
        StreakEntity existing = streak(5, 5, LocalDate.of(2026, 7, 23));
        when(streakRepository.findByUserIdAndStreakType(USER_ID, TYPE)).thenReturn(Optional.of(existing));

        StreakEntity result = streakService.recordActivity(USER_ID, TYPE, LocalDate.of(2026, 7, 24));

        assertEquals(6, result.getCurrentStreak());
        assertEquals(6, result.getLongestStreak());
    }

    @Test
    void gapInActivityResetsCurrentStreakButKeepsLongest() {
        StreakEntity existing = streak(10, 10, LocalDate.of(2026, 7, 1));
        when(streakRepository.findByUserIdAndStreakType(USER_ID, TYPE)).thenReturn(Optional.of(existing));

        StreakEntity result = streakService.recordActivity(USER_ID, TYPE, LocalDate.of(2026, 7, 24));

        assertEquals(1, result.getCurrentStreak());
        assertEquals(10, result.getLongestStreak(), "a broken streak must not erase the historical best");
    }

    @Test
    void recordingTwiceOnTheSameDayIsANoOp() {
        StreakEntity existing = streak(3, 3, LocalDate.of(2026, 7, 24));
        when(streakRepository.findByUserIdAndStreakType(USER_ID, TYPE)).thenReturn(Optional.of(existing));

        StreakEntity result = streakService.recordActivity(USER_ID, TYPE, LocalDate.of(2026, 7, 24));

        assertEquals(3, result.getCurrentStreak());
        org.mockito.Mockito.verify(streakRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
