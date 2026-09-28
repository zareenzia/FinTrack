package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.UserAchievementEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserAchievementRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserAchievementRepository repository;

    private UserAchievementEntity userAchievement(Long userId, Long achievementId, String status) {
        UserAchievementEntity a = new UserAchievementEntity();
        a.setUserId(userId);
        a.setAchievementId(achievementId);
        a.setStatus(status);
        a.setProgressCurrent(1.0);
        a.setProgressTarget(5.0);
        return a;
    }

    @Test
    void findByUserIdReturnsAllAchievementsForThatUser() {
        entityManager.persistAndFlush(userAchievement(1L, 10L, "LOCKED"));
        entityManager.persistAndFlush(userAchievement(1L, 11L, "UNLOCKED"));
        entityManager.persistAndFlush(userAchievement(2L, 10L, "LOCKED"));
        entityManager.clear();

        List<UserAchievementEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> a.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByUserIdAndAchievementIdReturnsMatchingRow() {
        entityManager.persistAndFlush(userAchievement(1L, 10L, "LOCKED"));
        entityManager.clear();

        Optional<UserAchievementEntity> found = repository.findByUserIdAndAchievementId(1L, 10L);

        assertTrue(found.isPresent());
        assertEquals("LOCKED", found.get().getStatus());
    }

    @Test
    void findByUserIdAndAchievementIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(userAchievement(1L, 10L, "LOCKED"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndAchievementId(1L, 999L).isEmpty());
        assertTrue(repository.findByUserIdAndAchievementId(2L, 10L).isEmpty());
    }

    @Test
    void countByUserIdAndStatusCountsOnlyMatchingStatusForThatUser() {
        entityManager.persistAndFlush(userAchievement(1L, 10L, "UNLOCKED"));
        entityManager.persistAndFlush(userAchievement(1L, 11L, "UNLOCKED"));
        entityManager.persistAndFlush(userAchievement(1L, 12L, "LOCKED"));
        entityManager.persistAndFlush(userAchievement(2L, 10L, "UNLOCKED"));
        entityManager.clear();

        assertEquals(2L, repository.countByUserIdAndStatus(1L, "UNLOCKED"));
        assertEquals(1L, repository.countByUserIdAndStatus(1L, "LOCKED"));
        assertEquals(0L, repository.countByUserIdAndStatus(1L, "SOMETHING_ELSE"));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersAchievements() {
        UserAchievementEntity a1 = entityManager.persistAndFlush(userAchievement(1L, 10L, "LOCKED"));
        UserAchievementEntity a2 = entityManager.persistAndFlush(userAchievement(2L, 10L, "LOCKED"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isPresent());
    }
}
