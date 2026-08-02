package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.BudgetTemplateCategoryEntity;
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
class BudgetTemplateCategoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BudgetTemplateCategoryRepository repository;

    private BudgetTemplateCategoryEntity templateCategory(Long templateId, Long categoryId, double amount, boolean isSavings) {
        BudgetTemplateCategoryEntity e = new BudgetTemplateCategoryEntity();
        e.setTemplateId(templateId);
        e.setCategoryId(categoryId);
        e.setPlannedAmount(amount);
        e.setIsSavings(isSavings);
        return e;
    }

    @Test
    void findByTemplateIdReturnsOnlyRowsForThatTemplate() {
        entityManager.persistAndFlush(templateCategory(1L, 5L, 100.0, false));
        entityManager.persistAndFlush(templateCategory(1L, 6L, 200.0, true));
        entityManager.persistAndFlush(templateCategory(2L, 7L, 300.0, false));
        entityManager.clear();

        List<BudgetTemplateCategoryEntity> result = repository.findByTemplateId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByTemplateIdReturnsEmptyWhenNoneForTemplate() {
        assertTrue(repository.findByTemplateId(999L).isEmpty());
    }

    @Test
    void deleteByTemplateIdRemovesOnlyRowsForThatTemplate() {
        BudgetTemplateCategoryEntity e1 = entityManager.persistAndFlush(templateCategory(1L, 5L, 100.0, false));
        BudgetTemplateCategoryEntity e2 = entityManager.persistAndFlush(templateCategory(2L, 7L, 300.0, false));
        entityManager.clear();

        repository.deleteByTemplateId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(e1.getId()).isEmpty());
        assertTrue(repository.findById(e2.getId()).isPresent());
    }
}
