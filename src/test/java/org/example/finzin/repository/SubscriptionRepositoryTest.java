package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SubscriptionEntity;
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
class SubscriptionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionRepository repository;

    private SubscriptionEntity subscription(Long userId, String name, String status) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.setUserId(userId);
        s.setName(name);
        s.setBillingCycle("MONTHLY");
        s.setCost(9.99);
        s.setAutoRenewal(true);
        s.setStatus(status);
        return s;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersSubscriptions() {
        entityManager.persistAndFlush(subscription(1L, "Netflix", "ACTIVE"));
        entityManager.persistAndFlush(subscription(1L, "Spotify", "ACTIVE"));
        entityManager.persistAndFlush(subscription(2L, "Disney+", "ACTIVE"));
        entityManager.clear();

        List<SubscriptionEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(subscription(1L, "Netflix", "ACTIVE"));
        entityManager.persistAndFlush(subscription(1L, "Old Gym", "CANCELLED"));
        entityManager.clear();

        List<SubscriptionEntity> result = repository.findByUserIdAndStatus(1L, "CANCELLED");

        assertEquals(1, result.size());
        assertEquals("Old Gym", result.get(0).getName());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(subscription(1L, "Netflix", "ACTIVE"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStatus(1L, "PAUSED").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSubscriptions() {
        SubscriptionEntity s1 = entityManager.persistAndFlush(subscription(1L, "Netflix", "ACTIVE"));
        SubscriptionEntity s2 = entityManager.persistAndFlush(subscription(2L, "Disney+", "ACTIVE"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
