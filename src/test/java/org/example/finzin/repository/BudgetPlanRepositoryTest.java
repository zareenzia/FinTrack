package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.BudgetPlanEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BudgetPlanRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BudgetPlanRepository repository;

    private BudgetPlanEntity plan(Long userId, String name, String status) {
        BudgetPlanEntity p = new BudgetPlanEntity();
        p.setUserId(userId);
        p.setName(name);
        p.setPeriodType("MONTH");
        p.setPeriod("2026-07");
        p.setStartDate(LocalDate.of(2026, 7, 1));
        p.setEndDate(LocalDate.of(2026, 7, 31));
        p.setPlannedIncome(1000.0);
        p.setPlannedSavings(100.0);
        p.setStatus(status);
        return p;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersPlans() {
        entityManager.persistAndFlush(plan(1L, "January", "ACTIVE"));
        entityManager.persistAndFlush(plan(1L, "February", "ACTIVE"));
        entityManager.persistAndFlush(plan(2L, "Other", "ACTIVE"));
        entityManager.clear();

        List<BudgetPlanEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(plan(1L, "January", "ACTIVE"));
        entityManager.persistAndFlush(plan(1L, "Old", "ARCHIVED"));
        entityManager.clear();

        List<BudgetPlanEntity> result = repository.findByUserIdAndStatus(1L, "ARCHIVED");

        assertEquals(1, result.size());
        assertEquals("Old", result.get(0).getName());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(plan(1L, "January", "ACTIVE"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStatus(1L, "ARCHIVED").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersPlans() {
        BudgetPlanEntity p1 = entityManager.persistAndFlush(plan(1L, "January", "ACTIVE"));
        BudgetPlanEntity p2 = entityManager.persistAndFlush(plan(2L, "Other", "ACTIVE"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(p1.getId()).isEmpty());
        assertTrue(repository.findById(p2.getId()).isPresent());
    }
}
