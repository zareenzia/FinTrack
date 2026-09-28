package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.XpHistoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class XpHistoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private XpHistoryRepository repository;

    private XpHistoryEntity xpHistory(Long userId, String sourceType, String sourceId, String reason) {
        XpHistoryEntity h = new XpHistoryEntity();
        h.setUserId(userId);
        h.setAmount(10);
        h.setReason(reason);
        h.setSourceType(sourceType);
        h.setSourceId(sourceId);
        return h;
    }

    @Test
    void findTop50ByUserIdOrderByCreatedAtDescOrdersNewestFirst() throws InterruptedException {
        XpHistoryEntity first = entityManager.persistFlushFind(xpHistory(1L, "ACHIEVEMENT", "1", "unlock"));
        Thread.sleep(5);
        XpHistoryEntity second = entityManager.persistFlushFind(xpHistory(1L, "ACHIEVEMENT", "2", "unlock"));
        Thread.sleep(5);
        entityManager.persistFlushFind(xpHistory(2L, "ACHIEVEMENT", "3", "unlock"));
        entityManager.clear();

        List<XpHistoryEntity> result = repository.findTop50ByUserIdOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "most recently created must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findTop50ByUserIdOrderByCreatedAtDescReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findTop50ByUserIdOrderByCreatedAtDesc(999L).isEmpty());
    }

    @Test
    void findTop50ByUserIdOrderByCreatedAtDescLimitsResultsTo50WhenMoreExist() {
        for (int i = 0; i < 52; i++) {
            entityManager.persist(xpHistory(1L, "DAILY_ACTIVE", "day-" + i, "daily"));
        }
        entityManager.flush();
        entityManager.clear();

        List<XpHistoryEntity> result = repository.findTop50ByUserIdOrderByCreatedAtDesc(1L);

        assertEquals(50, result.size());
    }

    @Test
    void existsByUserIdAndSourceTypeAndSourceIdAndReasonIsTrueWhenMatchExists() {
        entityManager.persistAndFlush(xpHistory(1L, "ACHIEVEMENT", "10", "unlock"));
        entityManager.clear();

        assertTrue(repository.existsByUserIdAndSourceTypeAndSourceIdAndReason(1L, "ACHIEVEMENT", "10", "unlock"));
    }

    @Test
    void existsByUserIdAndSourceTypeAndSourceIdAndReasonIsFalseWhenAnyFieldDiffers() {
        entityManager.persistAndFlush(xpHistory(1L, "ACHIEVEMENT", "10", "unlock"));
        entityManager.clear();

        assertFalse(repository.existsByUserIdAndSourceTypeAndSourceIdAndReason(2L, "ACHIEVEMENT", "10", "unlock"));
        assertFalse(repository.existsByUserIdAndSourceTypeAndSourceIdAndReason(1L, "CHALLENGE", "10", "unlock"));
        assertFalse(repository.existsByUserIdAndSourceTypeAndSourceIdAndReason(1L, "ACHIEVEMENT", "11", "unlock"));
        assertFalse(repository.existsByUserIdAndSourceTypeAndSourceIdAndReason(1L, "ACHIEVEMENT", "10", "other"));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersHistory() {
        XpHistoryEntity h1 = entityManager.persistAndFlush(xpHistory(1L, "ACHIEVEMENT", "10", "unlock"));
        XpHistoryEntity h2 = entityManager.persistAndFlush(xpHistory(2L, "ACHIEVEMENT", "10", "unlock"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(h1.getId()).isEmpty());
        assertTrue(repository.findById(h2.getId()).isPresent());
    }
}
