package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.TodoFolderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoFolderRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoFolderRepository repository;

    private TodoFolderEntity folder(Long userId, String name) {
        TodoFolderEntity f = new TodoFolderEntity();
        f.setUserId(userId);
        f.setName(name);
        return f;
    }

    @Test
    void findByUserIdReturnsOnlyFoldersForThatUser() {
        entityManager.persistAndFlush(folder(1L, "Work"));
        entityManager.persistAndFlush(folder(1L, "Personal"));
        entityManager.persistAndFlush(folder(2L, "Other"));
        entityManager.clear();

        List<TodoFolderEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(f -> f.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsFolderOnlyForOwningUser() {
        TodoFolderEntity saved = entityManager.persistAndFlush(folder(1L, "Work"));
        entityManager.clear();

        Optional<TodoFolderEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<TodoFolderEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertEquals("Work", found.get().getName());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersFolders() {
        TodoFolderEntity f1 = entityManager.persistAndFlush(folder(1L, "Work"));
        TodoFolderEntity f2 = entityManager.persistAndFlush(folder(2L, "Other"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(f1.getId()).isEmpty());
        assertTrue(repository.findById(f2.getId()).isPresent());
    }
}
