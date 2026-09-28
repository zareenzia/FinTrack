package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.UserXpEntity;
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
class UserXpRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserXpRepository repository;

    private UserXpEntity userXp(Long userId, long totalXp, int currentLevel) {
        UserXpEntity x = new UserXpEntity();
        x.setUserId(userId);
        x.setTotalXp(totalXp);
        x.setCurrentLevel(currentLevel);
        return x;
    }

    @Test
    void findByUserIdReturnsXpForThatUser() {
        entityManager.persistAndFlush(userXp(1L, 500L, 3));
        entityManager.clear();

        Optional<UserXpEntity> found = repository.findByUserId(1L);

        assertTrue(found.isPresent());
        assertEquals(500L, found.get().getTotalXp());
        assertEquals(3, found.get().getCurrentLevel());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersXp() {
        UserXpEntity x1 = entityManager.persistAndFlush(userXp(1L, 500L, 3));
        UserXpEntity x2 = entityManager.persistAndFlush(userXp(2L, 100L, 1));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(x1.getId()).isEmpty());
        assertTrue(repository.findById(x2.getId()).isPresent());
    }
}
