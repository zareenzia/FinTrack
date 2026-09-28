package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.GamificationSettingsEntity;
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
class GamificationSettingsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GamificationSettingsRepository repository;

    private GamificationSettingsEntity settings(Long userId) {
        GamificationSettingsEntity s = new GamificationSettingsEntity();
        s.setUserId(userId);
        s.setEnabled(true);
        s.setEnableNotifications(true);
        s.setShowDashboardWidget(true);
        s.setEnableCelebrations(true);
        s.setEnableChallenges(true);
        s.setEnableStreakTracking(true);
        s.setShowXp(true);
        return s;
    }

    @Test
    void findByUserIdReturnsSettingsForThatUser() {
        entityManager.persistAndFlush(settings(1L));
        entityManager.clear();

        Optional<GamificationSettingsEntity> found = repository.findByUserId(1L);

        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getUserId());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoSettingsForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSettings() {
        GamificationSettingsEntity s1 = entityManager.persistAndFlush(settings(1L));
        GamificationSettingsEntity s2 = entityManager.persistAndFlush(settings(2L));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
