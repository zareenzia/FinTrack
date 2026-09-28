package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.PasswordResetTokenEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordResetTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PasswordResetTokenRepository repository;

    private PasswordResetTokenEntity token(Long userId, String tokenHash) {
        PasswordResetTokenEntity t = new PasswordResetTokenEntity();
        t.setUserId(userId);
        t.setTokenHash(tokenHash);
        t.setExpiresAt(LocalDateTime.now().plusHours(1));
        return t;
    }

    @Test
    void findByTokenHashReturnsMatch() {
        entityManager.persistAndFlush(token(1L, "hash-abc-123"));
        entityManager.clear();

        Optional<PasswordResetTokenEntity> result = repository.findByTokenHash("hash-abc-123");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getUserId());
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByTokenHash("does-not-exist").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersTokens() {
        PasswordResetTokenEntity t1 = entityManager.persistAndFlush(token(1L, "hash-1"));
        PasswordResetTokenEntity t2 = entityManager.persistAndFlush(token(2L, "hash-2"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(t1.getId()).isEmpty());
        assertTrue(repository.findById(t2.getId()).isPresent());
    }
}
