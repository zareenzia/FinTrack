package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HouseholdRepository currently declares no custom query methods (plain JpaRepository), so this is
 * a small smoke test confirming the entity round-trips through save/findById with its @PrePersist
 * timestamps populated.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HouseholdRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdRepository householdRepository;

    @Test
    void savesAndFindsHouseholdById() {
        HouseholdEntity household = new HouseholdEntity();
        household.setName("The Smiths");
        household.setOwnerId(1L);

        HouseholdEntity saved = entityManager.persistAndFlush(household);
        entityManager.clear();

        Optional<HouseholdEntity> found = householdRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("The Smiths", found.get().getName());
        assertEquals(1L, found.get().getOwnerId());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
    }
}
