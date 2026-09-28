package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SharedTransactionShareEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedTransactionShareRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SharedTransactionShareRepository repository;

    private SharedTransactionShareEntity share(Long sharedTransactionId, Long userId, double amount, boolean isPayer) {
        SharedTransactionShareEntity s = new SharedTransactionShareEntity();
        s.setSharedTransactionId(sharedTransactionId);
        s.setUserId(userId);
        s.setShareAmount(amount);
        s.setIsPayer(isPayer);
        return s;
    }

    @Test
    void findBySharedTransactionIdReturnsAllSharesForThatTransaction() {
        entityManager.persistAndFlush(share(1L, 10L, 50.0, true));
        entityManager.persistAndFlush(share(1L, 11L, 50.0, false));
        entityManager.persistAndFlush(share(2L, 20L, 100.0, true));
        entityManager.clear();

        List<SharedTransactionShareEntity> result = repository.findBySharedTransactionId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findBySharedTransactionIdReturnsEmptyWhenNoneForTransaction() {
        assertTrue(repository.findBySharedTransactionId(999L).isEmpty());
    }

    @Test
    void findBySharedTransactionIdInReturnsOnlyMatchingShares() {
        entityManager.persistAndFlush(share(1L, 10L, 50.0, true));
        entityManager.persistAndFlush(share(2L, 20L, 100.0, true));
        entityManager.persistAndFlush(share(3L, 30L, 25.0, true));
        entityManager.clear();

        List<SharedTransactionShareEntity> result = repository.findBySharedTransactionIdIn(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(
                s -> s.getSharedTransactionId().equals(1L) || s.getSharedTransactionId().equals(2L)));
    }

    @Test
    void findBySharedTransactionIdInReturnsEmptyWhenNoIdsMatch() {
        entityManager.persistAndFlush(share(1L, 10L, 50.0, true));
        entityManager.clear();

        assertTrue(repository.findBySharedTransactionIdIn(List.of(998L, 999L)).isEmpty());
    }

    @Test
    void deleteBySharedTransactionIdRemovesOnlyThoseShares() {
        SharedTransactionShareEntity s1 = entityManager.persistAndFlush(share(1L, 10L, 50.0, true));
        SharedTransactionShareEntity s2 = entityManager.persistAndFlush(share(2L, 20L, 100.0, true));
        entityManager.clear();

        repository.deleteBySharedTransactionId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
