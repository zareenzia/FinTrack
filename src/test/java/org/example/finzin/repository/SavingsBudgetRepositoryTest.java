package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SavingsBudgetEntity;
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
class SavingsBudgetRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SavingsBudgetRepository repository;

    private SavingsBudgetEntity savingsBudget(Long budgetPlanId, Long categoryId, double target) {
        SavingsBudgetEntity s = new SavingsBudgetEntity();
        s.setBudgetPlanId(budgetPlanId);
        s.setCategoryId(categoryId);
        s.setTargetAmount(target);
        s.setInitialAmount(0.0);
        return s;
    }

    @Test
    void findByBudgetPlanIdReturnsAllSavingsBudgetsForThatPlan() {
        entityManager.persistAndFlush(savingsBudget(1L, 5L, 1000.0));
        entityManager.persistAndFlush(savingsBudget(1L, 6L, 2000.0));
        entityManager.persistAndFlush(savingsBudget(2L, 7L, 3000.0));
        entityManager.clear();

        List<SavingsBudgetEntity> result = repository.findByBudgetPlanId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByBudgetPlanIdReturnsEmptyWhenNoneForPlan() {
        assertTrue(repository.findByBudgetPlanId(999L).isEmpty());
    }

    @Test
    void findByBudgetPlanIdAndCategoryIdReturnsMatch() {
        entityManager.persistAndFlush(savingsBudget(1L, 5L, 1000.0));
        entityManager.clear();

        Optional<SavingsBudgetEntity> result = repository.findByBudgetPlanIdAndCategoryId(1L, 5L);

        assertTrue(result.isPresent());
        assertEquals(1000.0, result.get().getTargetAmount());
    }

    @Test
    void findByBudgetPlanIdAndCategoryIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(savingsBudget(1L, 5L, 1000.0));
        entityManager.clear();

        assertTrue(repository.findByBudgetPlanIdAndCategoryId(1L, 999L).isEmpty());
    }
}
