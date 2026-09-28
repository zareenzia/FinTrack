package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.ReceiptSettingsEntity;
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
class ReceiptSettingsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReceiptSettingsRepository repository;

    private ReceiptSettingsEntity settings(Long userId, boolean enabled) {
        ReceiptSettingsEntity s = new ReceiptSettingsEntity();
        s.setUserId(userId);
        s.setEnabled(enabled);
        return s;
    }

    @Test
    void findByUserIdReturnsSettingsForThatUser() {
        entityManager.persistAndFlush(settings(1L, false));
        entityManager.clear();

        Optional<ReceiptSettingsEntity> found = repository.findByUserId(1L);

        assertTrue(found.isPresent());
        assertEquals(false, found.get().getEnabled());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoSettingsForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSettings() {
        ReceiptSettingsEntity s1 = entityManager.persistAndFlush(settings(1L, true));
        ReceiptSettingsEntity s2 = entityManager.persistAndFlush(settings(2L, true));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
