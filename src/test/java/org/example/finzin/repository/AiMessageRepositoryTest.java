package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AiMessageEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: AiMessageEntity.onCreate() ({@code @PrePersist}) unconditionally sets createdAt to
 * {@code LocalDateTime.now()}, so the ordering test relies on real insertion order with a short
 * sleep to force strictly increasing timestamps, matching the convention used in
 * NotificationRepositoryTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiMessageRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiMessageRepository repository;

    private AiMessageEntity message(Long conversationId, Long userId, String role, String content) {
        AiMessageEntity m = new AiMessageEntity();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void findByConversationIdOrderByCreatedAtAscOrdersOldestFirstAndFiltersByConversation() throws InterruptedException {
        AiMessageEntity first = entityManager.persistFlushFind(message(1L, 10L, "user", "Hello"));
        Thread.sleep(5);
        AiMessageEntity second = entityManager.persistFlushFind(message(1L, 10L, "assistant", "Hi, how can I help?"));
        Thread.sleep(5);
        entityManager.persistFlushFind(message(2L, 11L, "user", "Other conversation"));
        entityManager.clear();

        List<AiMessageEntity> result = repository.findByConversationIdOrderByCreatedAtAsc(1L);

        assertEquals(2, result.size());
        assertEquals(first.getId(), result.get(0).getId(), "the earliest message must come first");
        assertEquals(second.getId(), result.get(1).getId());
    }

    @Test
    void findByConversationIdOrderByCreatedAtAscReturnsEmptyWhenNoneForConversation() {
        assertTrue(repository.findByConversationIdOrderByCreatedAtAsc(999L).isEmpty());
    }

    @Test
    void deleteByConversationIdRemovesOnlyThatConversationsMessages() {
        AiMessageEntity m1 = entityManager.persistAndFlush(message(1L, 10L, "user", "Hello"));
        AiMessageEntity m2 = entityManager.persistAndFlush(message(2L, 11L, "user", "Other"));
        entityManager.clear();

        repository.deleteByConversationId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(m1.getId()).isEmpty());
        assertTrue(repository.findById(m2.getId()).isPresent());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersMessages() {
        AiMessageEntity m1 = entityManager.persistAndFlush(message(1L, 10L, "user", "Hello"));
        AiMessageEntity m2 = entityManager.persistAndFlush(message(2L, 11L, "user", "Other"));
        entityManager.clear();

        repository.deleteByUserId(10L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(m1.getId()).isEmpty());
        assertTrue(repository.findById(m2.getId()).isPresent());
    }
}
