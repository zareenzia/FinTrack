package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdGoalContributionEntity;
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
class HouseholdGoalContributionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdGoalContributionRepository repository;

    private HouseholdGoalContributionEntity contribution(Long householdGoalId, Long userId, Long transactionId,
                                                           double amount, LocalDate contributedAt) {
        HouseholdGoalContributionEntity c = new HouseholdGoalContributionEntity();
        c.setHouseholdGoalId(householdGoalId);
        c.setUserId(userId);
        c.setTransactionId(transactionId);
        c.setAmount(amount);
        c.setContributedAt(contributedAt);
        return c;
    }

    @Test
    void findByHouseholdGoalIdOrderByContributedAtDescOrdersNewestFirstAndFiltersByGoal() {
        HouseholdGoalContributionEntity c1 = entityManager.persistAndFlush(
                contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        HouseholdGoalContributionEntity c2 = entityManager.persistAndFlush(
                contribution(1L, 11L, 101L, 75.0, LocalDate.of(2026, 3, 1)));
        entityManager.persistAndFlush(contribution(2L, 10L, 102L, 25.0, LocalDate.of(2026, 2, 1)));
        entityManager.clear();

        List<HouseholdGoalContributionEntity> result = repository.findByHouseholdGoalIdOrderByContributedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(c2.getId(), result.get(0).getId());
        assertEquals(c1.getId(), result.get(1).getId());
    }

    @Test
    void findByHouseholdGoalIdOrderByContributedAtDescReturnsEmptyWhenNoneForGoal() {
        assertTrue(repository.findByHouseholdGoalIdOrderByContributedAtDesc(999L).isEmpty());
    }

    @Test
    void findByHouseholdGoalIdInReturnsOnlyMatchingGoals() {
        entityManager.persistAndFlush(contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        entityManager.persistAndFlush(contribution(2L, 10L, 101L, 25.0, LocalDate.of(2026, 1, 2)));
        entityManager.persistAndFlush(contribution(3L, 10L, 102L, 10.0, LocalDate.of(2026, 1, 3)));
        entityManager.clear();

        List<HouseholdGoalContributionEntity> result = repository.findByHouseholdGoalIdIn(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> c.getHouseholdGoalId().equals(1L) || c.getHouseholdGoalId().equals(2L)));
    }

    @Test
    void findByHouseholdGoalIdInReturnsEmptyWhenNoIdsMatch() {
        entityManager.persistAndFlush(contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        entityManager.clear();

        assertTrue(repository.findByHouseholdGoalIdIn(List.of(998L, 999L)).isEmpty());
    }

    @Test
    void findByTransactionIdReturnsMatchingContribution() {
        entityManager.persistAndFlush(contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        entityManager.clear();

        Optional<HouseholdGoalContributionEntity> result = repository.findByTransactionId(100L);

        assertTrue(result.isPresent());
        assertEquals(50.0, result.get().getAmount());
    }

    @Test
    void findByTransactionIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByTransactionId(999L).isEmpty());
    }

    @Test
    void deleteByHouseholdGoalIdRemovesOnlyThatGoalsContributions() {
        HouseholdGoalContributionEntity c1 = entityManager.persistAndFlush(
                contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        HouseholdGoalContributionEntity c2 = entityManager.persistAndFlush(
                contribution(2L, 10L, 101L, 25.0, LocalDate.of(2026, 1, 2)));
        entityManager.clear();

        repository.deleteByHouseholdGoalId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }

    @Test
    void deleteByTransactionIdRemovesOnlyThatTransactionsContribution() {
        HouseholdGoalContributionEntity c1 = entityManager.persistAndFlush(
                contribution(1L, 10L, 100L, 50.0, LocalDate.of(2026, 1, 1)));
        HouseholdGoalContributionEntity c2 = entityManager.persistAndFlush(
                contribution(1L, 10L, 101L, 25.0, LocalDate.of(2026, 1, 2)));
        entityManager.clear();

        repository.deleteByTransactionId(100L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
