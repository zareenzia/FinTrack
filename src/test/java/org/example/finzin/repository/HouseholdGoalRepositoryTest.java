package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: HouseholdGoalEntity.onCreate() ({@code @PrePersist}) unconditionally overwrites createdAt
 * with {@code LocalDateTime.now()}, and the column is {@code updatable = false}, so ordering tests
 * rely on real insertion order with a short sleep to force strictly increasing timestamps, matching
 * the convention used in NotificationRepositoryTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HouseholdGoalRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdGoalRepository repository;

    private HouseholdGoalEntity goal(Long householdId, String name, double targetAmount) {
        HouseholdGoalEntity g = new HouseholdGoalEntity();
        g.setHouseholdId(householdId);
        g.setName(name);
        g.setTargetAmount(targetAmount);
        g.setCreatedByUserId(1L);
        return g;
    }

    @Test
    void findByHouseholdIdOrderByCreatedAtDescOrdersNewestFirstAndFiltersByHousehold() throws InterruptedException {
        HouseholdGoalEntity first = entityManager.persistFlushFind(goal(1L, "Vacation", 2000.0));
        Thread.sleep(5);
        HouseholdGoalEntity second = entityManager.persistFlushFind(goal(1L, "New Car", 15000.0));
        Thread.sleep(5);
        entityManager.persistFlushFind(goal(2L, "Other Household Goal", 500.0));
        entityManager.clear();

        List<HouseholdGoalEntity> result = repository.findByHouseholdIdOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "the most recently created goal must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findByHouseholdIdOrderByCreatedAtDescReturnsEmptyWhenNoneForHousehold() {
        List<HouseholdGoalEntity> result = repository.findByHouseholdIdOrderByCreatedAtDesc(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsGoals() {
        HouseholdGoalEntity g1 = entityManager.persistAndFlush(goal(1L, "Vacation", 2000.0));
        HouseholdGoalEntity g2 = entityManager.persistAndFlush(goal(2L, "Other Household Goal", 500.0));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(g1.getId()).isEmpty());
        assertTrue(repository.findById(g2.getId()).isPresent());
    }
}
