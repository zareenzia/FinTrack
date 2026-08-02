package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.UserStatCounterEntity;
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
class UserStatCounterRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserStatCounterRepository repository;

    private UserStatCounterEntity counter(Long userId, String counterKey, double value) {
        UserStatCounterEntity c = new UserStatCounterEntity();
        c.setUserId(userId);
        c.setCounterKey(counterKey);
        c.setCounterValue(value);
        return c;
    }

    @Test
    void findByUserIdAndCounterKeyReturnsMatchingCounter() {
        entityManager.persistAndFlush(counter(1L, "transactions.count", 7.0));
        entityManager.persistAndFlush(counter(1L, "savings.sum", 250.0));
        entityManager.clear();

        Optional<UserStatCounterEntity> found = repository.findByUserIdAndCounterKey(1L, "transactions.count");

        assertTrue(found.isPresent());
        assertEquals(7.0, found.get().getCounterValue());
    }

    @Test
    void findByUserIdAndCounterKeyReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(counter(1L, "transactions.count", 7.0));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndCounterKey(1L, "savings.sum").isEmpty());
        assertTrue(repository.findByUserIdAndCounterKey(2L, "transactions.count").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersCounters() {
        UserStatCounterEntity c1 = entityManager.persistAndFlush(counter(1L, "transactions.count", 7.0));
        UserStatCounterEntity c2 = entityManager.persistAndFlush(counter(2L, "transactions.count", 3.0));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
