package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AiConversationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: AiConversationEntity.onCreate() ({@code @PrePersist}) unconditionally sets both createdAt
 * and updatedAt to {@code LocalDateTime.now()}, so ordering tests rely on real insertion order with
 * a short sleep to force strictly increasing timestamps, matching the convention used in
 * NotificationRepositoryTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiConversationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiConversationRepository repository;

    private AiConversationEntity conversation(Long userId, String title) {
        AiConversationEntity c = new AiConversationEntity();
        c.setUserId(userId);
        c.setTitle(title);
        return c;
    }

    @Test
    void findByUserIdOrderByUpdatedAtDescOrdersMostRecentlyUpdatedFirst() throws InterruptedException {
        AiConversationEntity first = entityManager.persistFlushFind(conversation(1L, "Budget help"));
        Thread.sleep(5);
        AiConversationEntity second = entityManager.persistFlushFind(conversation(1L, "Savings advice"));
        Thread.sleep(5);
        entityManager.persistFlushFind(conversation(2L, "Other user conversation"));
        entityManager.clear();

        List<AiConversationEntity> result = repository.findByUserIdOrderByUpdatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId());
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findByUserIdOrderByUpdatedAtDescReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findByUserIdOrderByUpdatedAtDesc(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersConversations() {
        AiConversationEntity c1 = entityManager.persistAndFlush(conversation(1L, "Budget help"));
        AiConversationEntity c2 = entityManager.persistAndFlush(conversation(2L, "Other"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
