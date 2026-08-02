package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdBudgetEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HouseholdBudgetRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdBudgetRepository repository;

    private HouseholdBudgetEntity budget(Long householdId, String categoryName, double monthlyLimit) {
        HouseholdBudgetEntity b = new HouseholdBudgetEntity();
        b.setHouseholdId(householdId);
        b.setCategoryName(categoryName);
        b.setMonthlyLimit(monthlyLimit);
        b.setCreatedByUserId(1L);
        return b;
    }

    @Test
    void findByHouseholdIdOrderByCategoryNameAscOrdersAlphabeticallyAndFiltersByHousehold() {
        entityManager.persistAndFlush(budget(1L, "Utilities", 200.0));
        entityManager.persistAndFlush(budget(1L, "Groceries", 500.0));
        entityManager.persistAndFlush(budget(1L, "Dining", 150.0));
        entityManager.persistAndFlush(budget(2L, "Other Household", 999.0));
        entityManager.clear();

        List<HouseholdBudgetEntity> result = repository.findByHouseholdIdOrderByCategoryNameAsc(1L);

        assertEquals(3, result.size());
        assertEquals("Dining", result.get(0).getCategoryName());
        assertEquals("Groceries", result.get(1).getCategoryName());
        assertEquals("Utilities", result.get(2).getCategoryName());
    }

    @Test
    void findByHouseholdIdOrderByCategoryNameAscReturnsEmptyWhenNoneForHousehold() {
        List<HouseholdBudgetEntity> result = repository.findByHouseholdIdOrderByCategoryNameAsc(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsBudgets() {
        HouseholdBudgetEntity b1 = entityManager.persistAndFlush(budget(1L, "Groceries", 500.0));
        HouseholdBudgetEntity b2 = entityManager.persistAndFlush(budget(1L, "Dining", 150.0));
        HouseholdBudgetEntity b3 = entityManager.persistAndFlush(budget(2L, "Other Household", 999.0));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(b1.getId()).isEmpty());
        assertTrue(repository.findById(b2.getId()).isEmpty());
        assertTrue(repository.findById(b3.getId()).isPresent());
    }
}
