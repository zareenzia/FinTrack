package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.CategoryEntity;
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
class CategoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepository repository;

    private CategoryEntity category(Long userId, String name, String description) {
        return new CategoryEntity(userId, name, description, "#ff0000", "tag");
    }

    @Test
    void existsByNameIgnoreCaseIsTrueRegardlessOfCase() {
        entityManager.persistAndFlush(category(1L, "Dining", "food"));
        entityManager.clear();

        assertTrue(repository.existsByNameIgnoreCase("dining"));
        assertTrue(repository.existsByNameIgnoreCase("DINING"));
    }

    @Test
    void existsByNameIgnoreCaseIsFalseWhenNoMatch() {
        assertFalse(repository.existsByNameIgnoreCase("Nonexistent"));
    }

    @Test
    void existsByUserIdAndNameIgnoreCaseScopesToUser() {
        entityManager.persistAndFlush(category(1L, "Dining", "food"));
        entityManager.clear();

        assertTrue(repository.existsByUserIdAndNameIgnoreCase(1L, "dining"));
        assertFalse(repository.existsByUserIdAndNameIgnoreCase(2L, "dining"));
    }

    @Test
    void findByUserIdAndNameReturnsMatch() {
        entityManager.persistAndFlush(category(1L, "Dining", "food"));
        entityManager.clear();

        Optional<CategoryEntity> result = repository.findByUserIdAndName(1L, "Dining");

        assertTrue(result.isPresent());
        assertEquals("Dining", result.get().getName());
    }

    @Test
    void findByUserIdAndNameReturnsEmptyWhenNotFound() {
        Optional<CategoryEntity> result = repository.findByUserIdAndName(1L, "Nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersCategories() {
        entityManager.persistAndFlush(category(1L, "Dining", "food"));
        entityManager.persistAndFlush(category(1L, "Groceries", "food"));
        entityManager.persistAndFlush(category(2L, "Other", "food"));
        entityManager.clear();

        List<CategoryEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void searchByUserIdMatchesNameOrDescriptionCaseInsensitiveSubstring() {
        entityManager.persistAndFlush(category(1L, "Dining Out", "Restaurants and takeout"));
        entityManager.persistAndFlush(category(1L, "Groceries", "Supermarket trips"));
        entityManager.clear();

        List<CategoryEntity> byName = repository.searchByUserId(1L, "dining");
        List<CategoryEntity> byDescription = repository.searchByUserId(1L, "supermarket");

        assertEquals(1, byName.size());
        assertEquals("Dining Out", byName.get(0).getName());
        assertEquals(1, byDescription.size());
        assertEquals("Groceries", byDescription.get(0).getName());
    }

    @Test
    void searchByUserIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(category(1L, "Dining Out", "Restaurants and takeout"));
        entityManager.clear();

        List<CategoryEntity> result = repository.searchByUserId(1L, "zzz-nomatch");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByUserIdAndCategoryTypeOrGeneralIncludesExactTypeGeneralAndNullTypes() {
        CategoryEntity expenseOnly = category(1L, "Rent", "housing");
        expenseOnly.setCategoryType("expense");
        CategoryEntity generalType = category(1L, "Misc", "misc");
        generalType.setCategoryType("general");
        CategoryEntity nullType = category(1L, "Legacy", "legacy");
        nullType.setCategoryType(null);
        CategoryEntity incomeOnly = category(1L, "Salary", "income");
        incomeOnly.setCategoryType("income");
        entityManager.persistAndFlush(expenseOnly);
        entityManager.persistAndFlush(generalType);
        entityManager.persistAndFlush(nullType);
        entityManager.persistAndFlush(incomeOnly);
        entityManager.clear();

        List<CategoryEntity> result = repository.findByUserIdAndCategoryTypeOrGeneral(1L, "expense");

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getName().equals("Rent")));
        assertTrue(result.stream().anyMatch(c -> c.getName().equals("Misc")));
        assertTrue(result.stream().anyMatch(c -> c.getName().equals("Legacy")));
        assertFalse(result.stream().anyMatch(c -> c.getName().equals("Salary")));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersCategories() {
        CategoryEntity c1 = entityManager.persistAndFlush(category(1L, "Dining", "food"));
        CategoryEntity c2 = entityManager.persistAndFlush(category(2L, "Other", "food"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
