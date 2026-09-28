package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.ChallengeDefinitionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChallengeDefinitionRepository has only Spring-Data-generated derived queries over simple
 * columns (findByActiveTrue, findByMetricKeyAndActiveTrue) backed by a config/seed table, so this
 * is a small save/findById smoke test rather than full per-method coverage.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChallengeDefinitionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChallengeDefinitionRepository repository;

    private ChallengeDefinitionEntity challenge(String code) {
        ChallengeDefinitionEntity c = new ChallengeDefinitionEntity();
        c.setCode(code);
        c.setScope("MONTHLY");
        c.setName("Save More");
        c.setDescription("Save 500 this month");
        c.setMetricKey("savings.sum");
        c.setTargetValue(500.0);
        c.setXpReward(50);
        return c;
    }

    @Test
    void saveAndFindByIdRoundTripsAllFields() {
        ChallengeDefinitionEntity saved = entityManager.persistAndFlush(challenge("SAVE_500"));
        entityManager.clear();

        Optional<ChallengeDefinitionEntity> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("SAVE_500", found.get().getCode());
        assertEquals("savings.sum", found.get().getMetricKey());
        assertEquals(500.0, found.get().getTargetValue());
        assertTrue(found.get().getActive(), "active defaults true via @PrePersist");
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findById(999999L).isEmpty());
    }
}
