package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.NotificationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: NotificationEntity.onCreate() ({@code @PrePersist}) unconditionally overwrites createdAt
 * with {@code LocalDateTime.now()}, and the column is {@code updatable = false}, so callers cannot
 * pin an arbitrary createdAt value for a persisted row (it would silently be discarded). Tests that
 * care about createdAt ordering therefore rely on real insertion order (with a short sleep to force
 * strictly increasing timestamps) or on timestamps captured immediately before/after the actual
 * persist call, rather than trying to set createdAt directly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository repository;

    private NotificationEntity notification(Long userId, String type, boolean isRead, Long relatedEntityId) {
        NotificationEntity n = new NotificationEntity();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle("Title");
        n.setMessage("Message body");
        n.setRelatedEntityType("BUDGET_CATEGORY");
        n.setRelatedEntityId(relatedEntityId);
        n.setIsRead(isRead);
        return n;
    }

    @Test
    void findByUserIdOrderByCreatedAtDescOrdersNewestFirst() throws InterruptedException {
        NotificationEntity first = entityManager.persistFlushFind(notification(1L, "INFO", false, 1L));
        Thread.sleep(5);
        NotificationEntity second = entityManager.persistFlushFind(notification(1L, "INFO", false, 2L));
        Thread.sleep(5);
        entityManager.persistFlushFind(notification(2L, "INFO", false, 3L));
        entityManager.clear();

        List<NotificationEntity> result = repository.findByUserIdOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "the most recently created notification must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void countByUserIdAndIsReadFalseCountsOnlyUnreadForThatUser() {
        entityManager.persist(notification(1L, "INFO", false, 1L));
        entityManager.persist(notification(1L, "INFO", true, 2L));
        entityManager.persist(notification(2L, "INFO", false, 3L));
        entityManager.flush();
        entityManager.clear();

        assertEquals(1L, repository.countByUserIdAndIsReadFalse(1L));
    }

    @Test
    void existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfterIsTrueOnlyWithinWindow() {
        LocalDateTime beforeInsert = LocalDateTime.now();
        entityManager.persistAndFlush(notification(1L, "BUDGET_EXCEEDED", false, 10L));
        entityManager.clear();
        LocalDateTime afterInsert = LocalDateTime.now();

        boolean recentExists = repository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                1L, "BUDGET_EXCEEDED", 10L, beforeInsert.minusSeconds(1));
        boolean tooRecentWindowExists = repository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                1L, "BUDGET_EXCEEDED", 10L, afterInsert.plusSeconds(1));

        assertTrue(recentExists, "actual createdAt is after the cutoff (just before the insert), so it must count as recent");
        assertFalse(tooRecentWindowExists, "the cutoff is after the actual createdAt, so it must not count");
    }

    @Test
    void existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfterIsFalseForDifferentTypeOrEntity() {
        entityManager.persistAndFlush(notification(1L, "BUDGET_EXCEEDED", false, 10L));
        entityManager.clear();

        assertFalse(repository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                1L, "SAVINGS_GOAL_ACHIEVED", 10L, LocalDateTime.now().minusHours(1)));
        assertFalse(repository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                1L, "BUDGET_EXCEEDED", 999L, LocalDateTime.now().minusHours(1)));
    }

    @Test
    void markAllReadByUserIdMarksOnlyThatUsersUnreadNotificationsAndReturnsUpdateCount() {
        entityManager.persist(notification(1L, "INFO", false, 1L));
        entityManager.persist(notification(1L, "INFO", false, 2L));
        entityManager.persist(notification(1L, "INFO", true, 3L));
        entityManager.persist(notification(2L, "INFO", false, 4L));
        entityManager.flush();
        entityManager.clear();

        int updated = repository.markAllReadByUserId(1L);
        entityManager.clear();

        assertEquals(2, updated);
        assertEquals(0L, repository.countByUserIdAndIsReadFalse(1L));
        assertEquals(1L, repository.countByUserIdAndIsReadFalse(2L), "other users' notifications must be untouched");
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersNotifications() {
        NotificationEntity n1 = entityManager.persistFlushFind(notification(1L, "INFO", false, 1L));
        NotificationEntity n2 = entityManager.persistFlushFind(notification(2L, "INFO", false, 2L));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(n1.getId()).isEmpty());
        assertTrue(repository.findById(n2.getId()).isPresent());
    }
}
