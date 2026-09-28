package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.GoldPriceSettingEntity;
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
class GoldPriceSettingRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GoldPriceSettingRepository repository;

    private GoldPriceSettingEntity setting(Long userId, String mode) {
        GoldPriceSettingEntity s = new GoldPriceSettingEntity();
        s.setUserId(userId);
        s.setMode(mode);
        return s;
    }

    @Test
    void findByUserIdReturnsMatchingSetting() {
        entityManager.persistAndFlush(setting(1L, "MANUAL"));
        entityManager.persistAndFlush(setting(2L, "AUTOMATIC"));
        entityManager.clear();

        Optional<GoldPriceSettingEntity> result = repository.findByUserId(1L);

        assertTrue(result.isPresent());
        assertEquals("MANUAL", result.get().getMode());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSetting() {
        GoldPriceSettingEntity s1 = entityManager.persistAndFlush(setting(1L, "MANUAL"));
        GoldPriceSettingEntity s2 = entityManager.persistAndFlush(setting(2L, "AUTOMATIC"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
