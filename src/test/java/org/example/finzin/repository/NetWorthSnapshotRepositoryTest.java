package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.NetWorthSnapshotEntity;
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
class NetWorthSnapshotRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NetWorthSnapshotRepository repository;

    private NetWorthSnapshotEntity snapshot(Long userId, String month, double netWorth) {
        NetWorthSnapshotEntity s = new NetWorthSnapshotEntity();
        s.setUserId(userId);
        s.setSnapshotMonth(month);
        s.setNetWorth(netWorth);
        s.setTotalAssets(netWorth + 1000.0);
        s.setBalance(500.0);
        s.setTotalSavingsContributed(200.0);
        return s;
    }

    @Test
    void findByUserIdAndSnapshotMonthReturnsMatch() {
        entityManager.persistAndFlush(snapshot(1L, "2026-07", 10000.0));
        entityManager.clear();

        Optional<NetWorthSnapshotEntity> result = repository.findByUserIdAndSnapshotMonth(1L, "2026-07");

        assertTrue(result.isPresent());
        assertEquals(10000.0, result.get().getNetWorth());
    }

    @Test
    void findByUserIdAndSnapshotMonthReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(snapshot(1L, "2026-07", 10000.0));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndSnapshotMonth(1L, "2026-08").isEmpty());
    }

    @Test
    void findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDescReturnsMostRecentPriorMonth() {
        entityManager.persistAndFlush(snapshot(1L, "2026-05", 5000.0));
        entityManager.persistAndFlush(snapshot(1L, "2026-06", 8000.0));
        entityManager.persistAndFlush(snapshot(1L, "2026-07", 10000.0));
        entityManager.clear();

        Optional<NetWorthSnapshotEntity> result =
                repository.findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDesc(1L, "2026-07");

        assertTrue(result.isPresent());
        assertEquals("2026-06", result.get().getSnapshotMonth(), "must pick the most recent month strictly before the given one");
    }

    @Test
    void findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDescReturnsEmptyWhenNoPriorMonth() {
        entityManager.persistAndFlush(snapshot(1L, "2026-07", 10000.0));
        entityManager.clear();

        assertTrue(repository.findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDesc(1L, "2026-07").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSnapshots() {
        NetWorthSnapshotEntity s1 = entityManager.persistAndFlush(snapshot(1L, "2026-07", 10000.0));
        NetWorthSnapshotEntity s2 = entityManager.persistAndFlush(snapshot(2L, "2026-07", 20000.0));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
