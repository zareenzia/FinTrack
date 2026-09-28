package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AssetEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssetRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AssetRepository repository;

    private AssetEntity asset(Long userId, String name, double value) {
        return new AssetEntity(userId, name, "REAL_ESTATE", "desc", value, LocalDateTime.now());
    }

    @Test
    void sumAllValuesSumsAcrossAllUsers() {
        entityManager.persistAndFlush(asset(1L, "House", 100000.0));
        entityManager.persistAndFlush(asset(2L, "Car", 20000.0));
        entityManager.clear();

        Double sum = repository.sumAllValues();

        assertEquals(120000.0, sum);
    }

    @Test
    void sumAllValuesReturnsNullWhenNoAssetsExist() {
        assertNull(repository.sumAllValues());
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersAssets() {
        entityManager.persistAndFlush(asset(1L, "House", 100000.0));
        entityManager.persistAndFlush(asset(1L, "Car", 20000.0));
        entityManager.persistAndFlush(asset(2L, "Boat", 30000.0));
        entityManager.clear();

        List<AssetEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void sumValueByUserIdAndSumValuesByUserIdBothSumOnlyThatUsersAssets() {
        entityManager.persistAndFlush(asset(1L, "House", 100000.0));
        entityManager.persistAndFlush(asset(1L, "Car", 20000.0));
        entityManager.persistAndFlush(asset(2L, "Boat", 30000.0));
        entityManager.clear();

        assertEquals(120000.0, repository.sumValueByUserId(1L));
        assertEquals(120000.0, repository.sumValuesByUserId(1L));
    }

    @Test
    void sumValueByUserIdReturnsNullWhenUserHasNoAssets() {
        assertNull(repository.sumValueByUserId(999L));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersAssets() {
        AssetEntity a1 = entityManager.persistAndFlush(asset(1L, "House", 100000.0));
        AssetEntity a2 = entityManager.persistAndFlush(asset(2L, "Boat", 30000.0));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isPresent());
    }
}
