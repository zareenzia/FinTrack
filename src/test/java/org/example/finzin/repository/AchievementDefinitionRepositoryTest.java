package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AchievementDefinitionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AchievementDefinitionRepository has only Spring-Data-generated derived queries over simple
 * columns (findByActiveTrue, findByMetricKeyAndActiveTrue, findByCode) backed by a config/seed
 * table, so this is a small save/findById smoke test rather than full per-method coverage.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AchievementDefinitionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AchievementDefinitionRepository repository;

    private AchievementDefinitionEntity achievement(String code) {
        AchievementDefinitionEntity a = new AchievementDefinitionEntity();
        a.setCode(code);
        a.setCategory("SPENDING");
        a.setName("First Steps");
        a.setDescription("Log your first transaction");
        a.setIcon("star");
        a.setTierColor("bronze");
        a.setCriteriaType("COUNT");
        a.setMetricKey("transactions.count");
        a.setThreshold(1.0);
        a.setXpReward(10);
        return a;
    }

    @Test
    void saveAndFindByIdRoundTripsAllFields() {
        AchievementDefinitionEntity saved = entityManager.persistAndFlush(achievement("FIRST_TX"));
        entityManager.clear();

        Optional<AchievementDefinitionEntity> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("FIRST_TX", found.get().getCode());
        assertEquals("transactions.count", found.get().getMetricKey());
        assertEquals(10, found.get().getXpReward());
        assertTrue(found.get().getActive(), "active defaults true via @PrePersist");
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findById(999999L).isEmpty());
    }
}
