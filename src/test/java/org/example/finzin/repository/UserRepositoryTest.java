package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository repository;

    private UserEntity user(String username, String email) {
        UserEntity u = new UserEntity();
        u.setFullName("Test User");
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("hashed-password");
        return u;
    }

    @Test
    void findByUsernameReturnsMatch() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        Optional<UserEntity> result = repository.findByUsername("nabilh");

        assertTrue(result.isPresent());
    }

    @Test
    void findByUsernameReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUsername("nonexistent").isEmpty());
    }

    @Test
    void findByEmailReturnsMatch() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        Optional<UserEntity> result = repository.findByEmail("nabil@example.com");

        assertTrue(result.isPresent());
    }

    @Test
    void findByEmailReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByEmail("nobody@example.com").isEmpty());
    }

    @Test
    void findByUsernameOrEmailMatchesEitherFieldCaseInsensitively() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        assertTrue(repository.findByUsernameOrEmail("NABILH", "someone-else@example.com").isPresent());
        assertTrue(repository.findByUsernameOrEmail("someone-else", "NABIL@EXAMPLE.COM").isPresent());
    }

    @Test
    void findByUsernameOrEmailReturnsEmptyWhenNeitherMatches() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        assertTrue(repository.findByUsernameOrEmail("nobody", "nobody@example.com").isEmpty());
    }

    @Test
    void existsByUsernameIgnoreCaseIsTrueRegardlessOfCase() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        assertTrue(repository.existsByUsernameIgnoreCase("NabilH"));
        assertFalse(repository.existsByUsernameIgnoreCase("someoneelse"));
    }

    @Test
    void existsByEmailIgnoreCaseIsTrueRegardlessOfCase() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        assertTrue(repository.existsByEmailIgnoreCase("NABIL@EXAMPLE.COM"));
        assertFalse(repository.existsByEmailIgnoreCase("other@example.com"));
    }

    @Test
    void findByUsernameIgnoreCaseReturnsMatchRegardlessOfCase() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        Optional<UserEntity> result = repository.findByUsernameIgnoreCase("NABILH");

        assertTrue(result.isPresent());
        assertEquals("nabilh", result.get().getUsername());
    }

    @Test
    void findByEmailIgnoreCaseReturnsMatchRegardlessOfCase() {
        entityManager.persistAndFlush(user("nabilh", "nabil@example.com"));
        entityManager.clear();

        Optional<UserEntity> result = repository.findByEmailIgnoreCase("NABIL@EXAMPLE.COM");

        assertTrue(result.isPresent());
        assertEquals("nabil@example.com", result.get().getEmail());
    }
}
