package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.GoldAssetEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoldAssetRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GoldAssetRepository repository;

    private GoldAssetEntity asset(Long userId, double weight, String weightUnit, Double currentValue) {
        GoldAssetEntity a = new GoldAssetEntity();
        a.setUserId(userId);
        a.setAssetName("Wedding Ring");
        a.setGoldType("ORNAMENT");
        a.setPurity("22K");
        a.setWeight(weight);
        a.setWeightUnit(weightUnit);
        a.setCurrentValue(currentValue);
        return a;
    }

    @Test
    void findByUserIdReturnsOnlyAssetsForThatUser() {
        entityManager.persistAndFlush(asset(1L, 10.0, "GRAM", 1000.0));
        entityManager.persistAndFlush(asset(1L, 5.0, "GRAM", 500.0));
        entityManager.persistAndFlush(asset(2L, 20.0, "GRAM", 2000.0));
        entityManager.clear();

        List<GoldAssetEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> a.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoAssetsForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void sumCurrentValueByUserIdSumsOnlyNonNullValuesForThatUser() {
        entityManager.persistAndFlush(asset(1L, 10.0, "GRAM", 1000.0));
        entityManager.persistAndFlush(asset(1L, 5.0, "GRAM", 500.0));
        entityManager.persistAndFlush(asset(1L, 2.0, "GRAM", null));
        entityManager.persistAndFlush(asset(2L, 20.0, "GRAM", 2000.0));
        entityManager.clear();

        Double sum = repository.sumCurrentValueByUserId(1L);

        assertEquals(1500.0, sum, 0.0001);
    }

    @Test
    void sumCurrentValueByUserIdReturnsNullWhenUserHasNoAssets() {
        assertNull(repository.sumCurrentValueByUserId(999L));
    }

    @Test
    void sumWeightInGramsByUserIdConvertsEachUnitToGrams() {
        entityManager.persistAndFlush(asset(1L, 10.0, "GRAM", null));
        entityManager.persistAndFlush(asset(1L, 1.0, "VORI", null));
        entityManager.persistAndFlush(asset(1L, 2.0, "ANA", null));
        entityManager.persistAndFlush(asset(2L, 100.0, "GRAM", null));
        entityManager.clear();

        Double sum = repository.sumWeightInGramsByUserId(1L);

        double expected = 10.0 * 1.0 + 1.0 * 11.664 + 2.0 * 0.729;
        assertEquals(expected, sum, 0.0001);
    }

    @Test
    void sumWeightInGramsByUserIdReturnsNullWhenUserHasNoAssets() {
        assertNull(repository.sumWeightInGramsByUserId(999L));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersAssets() {
        GoldAssetEntity a1 = entityManager.persistAndFlush(asset(1L, 10.0, "GRAM", 1000.0));
        GoldAssetEntity a2 = entityManager.persistAndFlush(asset(2L, 20.0, "GRAM", 2000.0));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isPresent());
    }
}
