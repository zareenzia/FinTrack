package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.UserChallengeEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserChallengeRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserChallengeRepository repository;

    private UserChallengeEntity userChallenge(Long userId, Long challengeId, String periodKey, String status) {
        UserChallengeEntity c = new UserChallengeEntity();
        c.setUserId(userId);
        c.setChallengeId(challengeId);
        c.setPeriodKey(periodKey);
        c.setTargetValue(100.0);
        c.setStatus(status);
        return c;
    }

    @Test
    void existsByUserIdAndPeriodKeyIsTrueWhenRowExists() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        assertTrue(repository.existsByUserIdAndPeriodKey(1L, "2026-08"));
    }

    @Test
    void existsByUserIdAndPeriodKeyIsFalseForDifferentUserOrPeriod() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        assertFalse(repository.existsByUserIdAndPeriodKey(2L, "2026-08"));
        assertFalse(repository.existsByUserIdAndPeriodKey(1L, "2026-07"));
    }

    @Test
    void findByUserIdAndPeriodKeyReturnsAllChallengesInThatPeriod() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.persistAndFlush(userChallenge(1L, 11L, "2026-08", "COMPLETED"));
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-07", "COMPLETED"));
        entityManager.clear();

        List<UserChallengeEntity> result = repository.findByUserIdAndPeriodKey(1L, "2026-08");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> "2026-08".equals(c.getPeriodKey())));
    }

    @Test
    void findByUserIdAndPeriodKeyReturnsEmptyWhenNoMatch() {
        assertTrue(repository.findByUserIdAndPeriodKey(1L, "2026-08").isEmpty());
    }

    @Test
    void findByUserIdAndChallengeIdAndPeriodKeyReturnsMatchingRow() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        Optional<UserChallengeEntity> found = repository.findByUserIdAndChallengeIdAndPeriodKey(1L, 10L, "2026-08");

        assertTrue(found.isPresent());
        assertEquals("IN_PROGRESS", found.get().getStatus());
    }

    @Test
    void findByUserIdAndChallengeIdAndPeriodKeyReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndChallengeIdAndPeriodKey(1L, 999L, "2026-08").isEmpty());
        assertTrue(repository.findByUserIdAndChallengeIdAndPeriodKey(1L, 10L, "2026-09").isEmpty());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.persistAndFlush(userChallenge(1L, 11L, "2026-08", "COMPLETED"));
        entityManager.clear();

        List<UserChallengeEntity> result = repository.findByUserIdAndStatus(1L, "COMPLETED");

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getChallengeId());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStatus(1L, "EXPIRED").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersChallenges() {
        UserChallengeEntity c1 = entityManager.persistAndFlush(userChallenge(1L, 10L, "2026-08", "IN_PROGRESS"));
        UserChallengeEntity c2 = entityManager.persistAndFlush(userChallenge(2L, 10L, "2026-08", "IN_PROGRESS"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
