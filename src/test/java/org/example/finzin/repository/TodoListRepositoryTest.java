package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.TodoListEntity;
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
class TodoListRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoListRepository repository;

    private TodoListEntity list(Long userId, String name, Long folderId) {
        TodoListEntity l = new TodoListEntity();
        l.setUserId(userId);
        l.setName(name);
        l.setFolderId(folderId);
        return l;
    }

    @Test
    void findByUserIdReturnsOnlyListsForThatUser() {
        entityManager.persistAndFlush(list(1L, "Groceries", null));
        entityManager.persistAndFlush(list(1L, "Chores", null));
        entityManager.persistAndFlush(list(2L, "Other", null));
        entityManager.clear();

        List<TodoListEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> l.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsListOnlyForOwningUser() {
        TodoListEntity saved = entityManager.persistAndFlush(list(1L, "Groceries", null));
        entityManager.clear();

        Optional<TodoListEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<TodoListEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertEquals("Groceries", found.get().getName());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void findByUserIdAndFolderIdReturnsOnlyListsInThatFolder() {
        entityManager.persistAndFlush(list(1L, "Groceries", 10L));
        entityManager.persistAndFlush(list(1L, "Recipes", 10L));
        entityManager.persistAndFlush(list(1L, "Standalone", null));
        entityManager.persistAndFlush(list(1L, "OtherFolder", 20L));
        entityManager.clear();

        List<TodoListEntity> result = repository.findByUserIdAndFolderId(1L, 10L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> l.getFolderId().equals(10L)));
    }

    @Test
    void findByUserIdAndFolderIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(list(1L, "Groceries", 10L));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndFolderId(1L, 999L).isEmpty());
    }

    @Test
    void findFirstByUserIdAndNameIgnoreCaseIsCaseInsensitive() {
        entityManager.persistAndFlush(list(1L, "Groceries", null));
        entityManager.clear();

        Optional<TodoListEntity> found = repository.findFirstByUserIdAndNameIgnoreCase(1L, "groceries");

        assertTrue(found.isPresent());
        assertEquals("Groceries", found.get().getName());
    }

    @Test
    void findFirstByUserIdAndNameIgnoreCaseReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(list(1L, "Groceries", null));
        entityManager.clear();

        assertTrue(repository.findFirstByUserIdAndNameIgnoreCase(1L, "Chores").isEmpty());
        assertTrue(repository.findFirstByUserIdAndNameIgnoreCase(2L, "Groceries").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersLists() {
        TodoListEntity l1 = entityManager.persistAndFlush(list(1L, "Groceries", null));
        TodoListEntity l2 = entityManager.persistAndFlush(list(2L, "Other", null));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(l1.getId()).isEmpty());
        assertTrue(repository.findById(l2.getId()).isPresent());
    }
}
