package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.StreakEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StreakRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StreakRepository repository;

    private StreakEntity streak(Long userId, String streakType, int currentStreak) {
        StreakEntity s = new StreakEntity();
        s.setUserId(userId);
        s.setStreakType(streakType);
        s.setCurrentStreak(currentStreak);
        s.setLongestStreak(currentStreak);
        s.setLastActivityDate(LocalDate.now());
        return s;
    }

    @Test
    void findByUserIdAndStreakTypeReturnsMatchingStreak() {
        entityManager.persistAndFlush(streak(1L, "DAILY_LOGIN", 5));
        entityManager.persistAndFlush(streak(1L, "BUDGET_ADHERENCE", 2));
        entityManager.clear();

        Optional<StreakEntity> found = repository.findByUserIdAndStreakType(1L, "DAILY_LOGIN");

        assertTrue(found.isPresent());
        assertEquals(5, found.get().getCurrentStreak());
    }

    @Test
    void findByUserIdAndStreakTypeReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(streak(1L, "DAILY_LOGIN", 5));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStreakType(1L, "BUDGET_ADHERENCE").isEmpty());
        assertTrue(repository.findByUserIdAndStreakType(2L, "DAILY_LOGIN").isEmpty());
    }

    @Test
    void findByUserIdReturnsAllStreaksForThatUser() {
        entityManager.persistAndFlush(streak(1L, "DAILY_LOGIN", 5));
        entityManager.persistAndFlush(streak(1L, "BUDGET_ADHERENCE", 2));
        entityManager.persistAndFlush(streak(2L, "DAILY_LOGIN", 1));
        entityManager.clear();

        List<StreakEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoStreaksForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersStreaks() {
        StreakEntity s1 = entityManager.persistAndFlush(streak(1L, "DAILY_LOGIN", 5));
        StreakEntity s2 = entityManager.persistAndFlush(streak(2L, "DAILY_LOGIN", 1));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
