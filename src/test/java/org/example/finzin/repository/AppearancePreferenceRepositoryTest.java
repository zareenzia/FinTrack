package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AppearancePreferenceEntity;
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
class AppearancePreferenceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AppearancePreferenceRepository repository;

    private AppearancePreferenceEntity preference(Long userId, String theme, String colorTheme) {
        AppearancePreferenceEntity p = new AppearancePreferenceEntity();
        p.setUserId(userId);
        p.setTheme(theme);
        p.setColorTheme(colorTheme);
        return p;
    }

    @Test
    void findByUserIdReturnsMatch() {
        entityManager.persistAndFlush(preference(1L, "dark", "ocean"));
        entityManager.clear();

        Optional<AppearancePreferenceEntity> result = repository.findByUserId(1L);

        assertTrue(result.isPresent());
        assertEquals("dark", result.get().getTheme());
        assertEquals("ocean", result.get().getColorTheme());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersPreference() {
        AppearancePreferenceEntity p1 = entityManager.persistAndFlush(preference(1L, "dark", "ocean"));
        AppearancePreferenceEntity p2 = entityManager.persistAndFlush(preference(2L, "light", "forest"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(p1.getId()).isEmpty());
        assertTrue(repository.findById(p2.getId()).isPresent());
    }
}
