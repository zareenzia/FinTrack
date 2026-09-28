package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SettlementEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SettlementRepository repository;

    private SettlementEntity settlement(Long householdId, Long fromUserId, Long toUserId, double amount, LocalDateTime settledAt) {
        SettlementEntity s = new SettlementEntity();
        s.setHouseholdId(householdId);
        s.setFromUserId(fromUserId);
        s.setToUserId(toUserId);
        s.setAmount(amount);
        s.setSettledAt(settledAt);
        return s;
    }

    @Test
    void findByHouseholdIdOrderBySettledAtDescOrdersNewestFirstAndFiltersByHousehold() {
        SettlementEntity s1 = entityManager.persistAndFlush(
                settlement(1L, 10L, 11L, 50.0, LocalDateTime.of(2026, 1, 1, 0, 0)));
        SettlementEntity s2 = entityManager.persistAndFlush(
                settlement(1L, 11L, 10L, 25.0, LocalDateTime.of(2026, 3, 1, 0, 0)));
        entityManager.persistAndFlush(settlement(2L, 20L, 21L, 100.0, LocalDateTime.of(2026, 2, 1, 0, 0)));
        entityManager.clear();

        List<SettlementEntity> result = repository.findByHouseholdIdOrderBySettledAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(s2.getId(), result.get(0).getId());
        assertEquals(s1.getId(), result.get(1).getId());
    }

    @Test
    void findByHouseholdIdOrderBySettledAtDescReturnsEmptyWhenNoneForHousehold() {
        assertTrue(repository.findByHouseholdIdOrderBySettledAtDesc(999L).isEmpty());
    }

    @Test
    void settledAtDefaultsToCreatedAtWhenNotProvided() {
        SettlementEntity saved = entityManager.persistFlushFind(settlement(1L, 10L, 11L, 50.0, null));

        assertEquals(saved.getCreatedAt(), saved.getSettledAt());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsSettlements() {
        SettlementEntity s1 = entityManager.persistAndFlush(settlement(1L, 10L, 11L, 50.0, LocalDateTime.now()));
        SettlementEntity s2 = entityManager.persistAndFlush(settlement(2L, 20L, 21L, 100.0, LocalDateTime.now()));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
