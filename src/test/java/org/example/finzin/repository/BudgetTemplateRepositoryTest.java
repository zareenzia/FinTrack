package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.BudgetTemplateEntity;
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
class BudgetTemplateRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BudgetTemplateRepository repository;

    private BudgetTemplateEntity template(Long userId, String name) {
        BudgetTemplateEntity t = new BudgetTemplateEntity();
        t.setUserId(userId);
        t.setName(name);
        t.setPlannedIncome(1000.0);
        t.setPlannedSavings(100.0);
        return t;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersTemplates() {
        entityManager.persistAndFlush(template(1L, "Standard Month"));
        entityManager.persistAndFlush(template(1L, "Tight Month"));
        entityManager.persistAndFlush(template(2L, "Other's Template"));
        entityManager.clear();

        List<BudgetTemplateEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersTemplates() {
        BudgetTemplateEntity t1 = entityManager.persistAndFlush(template(1L, "Standard Month"));
        BudgetTemplateEntity t2 = entityManager.persistAndFlush(template(2L, "Other's Template"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(t1.getId()).isEmpty());
        assertTrue(repository.findById(t2.getId()).isPresent());
    }
}
