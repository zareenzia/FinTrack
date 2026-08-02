package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SidebarPreferenceEntity;
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
class SidebarPreferenceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SidebarPreferenceRepository repository;

    private SidebarPreferenceEntity preference(Long userId, String json) {
        SidebarPreferenceEntity p = new SidebarPreferenceEntity();
        p.setUserId(userId);
        p.setPreferencesJson(json);
        return p;
    }

    @Test
    void findByUserIdReturnsMatch() {
        entityManager.persistAndFlush(preference(1L, "[{\"id\":\"dashboard\"}]"));
        entityManager.clear();

        Optional<SidebarPreferenceEntity> result = repository.findByUserId(1L);

        assertTrue(result.isPresent());
        assertEquals("[{\"id\":\"dashboard\"}]", result.get().getPreferencesJson());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersPreference() {
        SidebarPreferenceEntity p1 = entityManager.persistAndFlush(preference(1L, "[]"));
        SidebarPreferenceEntity p2 = entityManager.persistAndFlush(preference(2L, "[]"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(p1.getId()).isEmpty());
        assertTrue(repository.findById(p2.getId()).isPresent());
    }
}
