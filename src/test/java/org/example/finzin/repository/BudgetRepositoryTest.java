package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.BudgetEntity;
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
class BudgetRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BudgetRepository repository;

    private BudgetEntity budget(Long userId, Long budgetPlanId, Long categoryId, String period, double amount) {
        BudgetEntity b = new BudgetEntity();
        b.setUserId(userId);
        b.setBudgetPlanId(budgetPlanId);
        b.setCategoryId(categoryId);
        b.setPeriod(period);
        b.setBudgetAmount(amount);
        return b;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersBudgets() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.persistAndFlush(budget(1L, 1L, 6L, "2026-07", 200.0));
        entityManager.persistAndFlush(budget(2L, 2L, 7L, "2026-07", 300.0));
        entityManager.clear();

        List<BudgetEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdAndPeriodFiltersByPeriod() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.persistAndFlush(budget(1L, 1L, 6L, "2026-08", 200.0));
        entityManager.clear();

        List<BudgetEntity> result = repository.findByUserIdAndPeriod(1L, "2026-07");

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getCategoryId());
    }

    @Test
    void findByUserIdAndPeriodReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndPeriod(1L, "2099-01").isEmpty());
    }

    @Test
    void findByUserIdAndCategoryIdAndPeriodReturnsMatch() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.clear();

        Optional<BudgetEntity> result = repository.findByUserIdAndCategoryIdAndPeriod(1L, 5L, "2026-07");

        assertTrue(result.isPresent());
        assertEquals(100.0, result.get().getBudgetAmount());
    }

    @Test
    void findByUserIdAndCategoryIdAndPeriodReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndCategoryIdAndPeriod(1L, 999L, "2026-07").isEmpty());
    }

    @Test
    void findByBudgetPlanIdReturnsAllBudgetsForThatPlan() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.persistAndFlush(budget(1L, 1L, 6L, "2026-07", 200.0));
        entityManager.persistAndFlush(budget(1L, 2L, 7L, "2026-07", 300.0));
        entityManager.clear();

        List<BudgetEntity> result = repository.findByBudgetPlanId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByBudgetPlanIdAndCategoryIdReturnsMatch() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.clear();

        Optional<BudgetEntity> result = repository.findByBudgetPlanIdAndCategoryId(1L, 5L);

        assertTrue(result.isPresent());
    }

    @Test
    void findByBudgetPlanIdAndCategoryIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        entityManager.clear();

        assertTrue(repository.findByBudgetPlanIdAndCategoryId(1L, 999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersBudgets() {
        BudgetEntity b1 = entityManager.persistAndFlush(budget(1L, 1L, 5L, "2026-07", 100.0));
        BudgetEntity b2 = entityManager.persistAndFlush(budget(2L, 2L, 6L, "2026-07", 200.0));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(b1.getId()).isEmpty());
        assertTrue(repository.findById(b2.getId()).isPresent());
    }
}
