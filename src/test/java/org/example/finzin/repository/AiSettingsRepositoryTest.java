package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AiSettingsEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiSettingsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiSettingsRepository repository;

    private AiSettingsEntity settings(Long userId) {
        AiSettingsEntity s = new AiSettingsEntity();
        s.setUserId(userId);
        return s;
    }

    @Test
    void findByUserIdReturnsMatchingSettingsWithPrePersistDefaultsApplied() {
        entityManager.persistAndFlush(settings(1L));
        entityManager.persistAndFlush(settings(2L));
        entityManager.clear();

        Optional<AiSettingsEntity> result = repository.findByUserId(1L);

        assertTrue(result.isPresent());
        assertEquals("openai", result.get().getProvider());
        assertEquals("gpt-5", result.get().getModel());
        assertTrue(result.get().getEnabled());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSettings() {
        AiSettingsEntity s1 = entityManager.persistAndFlush(settings(1L));
        AiSettingsEntity s2 = entityManager.persistAndFlush(settings(2L));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
