package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.TodoItemEntity;
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
class TodoItemRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoItemRepository repository;

    private TodoItemEntity item(Long userId, Long listId, Long parentItemId, String title) {
        TodoItemEntity i = new TodoItemEntity();
        i.setUserId(userId);
        i.setListId(listId);
        i.setParentItemId(parentItemId);
        i.setTitle(title);
        i.setCompleted(false);
        i.setImportant(false);
        return i;
    }

    private TodoItemEntity important(Long userId, Long listId, String title) {
        TodoItemEntity i = item(userId, listId, null, title);
        i.setImportant(true);
        return i;
    }

    @Test
    void findByUserIdAndListIdAndParentItemIdIsNullReturnsOnlyTopLevelItems() {
        TodoItemEntity topLevel = entityManager.persistFlushFind(item(1L, 10L, null, "Buy milk"));
        entityManager.persistAndFlush(item(1L, 10L, topLevel.getId(), "Sub-step"));
        entityManager.persistAndFlush(item(1L, 20L, null, "Different list"));
        entityManager.persistAndFlush(item(2L, 10L, null, "Different user"));
        entityManager.clear();

        List<TodoItemEntity> result = repository.findByUserIdAndListIdAndParentItemIdIsNull(1L, 10L);

        assertEquals(1, result.size());
        assertEquals("Buy milk", result.get(0).getTitle());
    }

    @Test
    void findByUserIdAndListIdAndParentItemIdIsNullReturnsEmptyWhenNoneMatch() {
        assertTrue(repository.findByUserIdAndListIdAndParentItemIdIsNull(1L, 999L).isEmpty());
    }

    @Test
    void findByListIdReturnsAllItemsInListIncludingSubItems() {
        TodoItemEntity topLevel = entityManager.persistFlushFind(item(1L, 10L, null, "Buy milk"));
        entityManager.persistAndFlush(item(1L, 10L, topLevel.getId(), "Sub-step"));
        entityManager.persistAndFlush(item(1L, 20L, null, "Different list"));
        entityManager.clear();

        List<TodoItemEntity> result = repository.findByListId(10L);

        assertEquals(2, result.size());
    }

    @Test
    void findByListIdReturnsEmptyWhenNoItems() {
        assertTrue(repository.findByListId(999L).isEmpty());
    }

    @Test
    void findByParentItemIdReturnsOnlySubItemsOfThatParent() {
        TodoItemEntity parent = entityManager.persistFlushFind(item(1L, 10L, null, "Buy milk"));
        TodoItemEntity otherParent = entityManager.persistFlushFind(item(1L, 10L, null, "Buy eggs"));
        entityManager.persistAndFlush(item(1L, 10L, parent.getId(), "Pick 2%"));
        entityManager.persistAndFlush(item(1L, 10L, otherParent.getId(), "Pick dozen"));
        entityManager.clear();

        List<TodoItemEntity> result = repository.findByParentItemId(parent.getId());

        assertEquals(1, result.size());
        assertEquals("Pick 2%", result.get(0).getTitle());
    }

    @Test
    void findByParentItemIdReturnsEmptyWhenNoSubItems() {
        TodoItemEntity parent = entityManager.persistAndFlush(item(1L, 10L, null, "Buy milk"));
        entityManager.clear();

        assertTrue(repository.findByParentItemId(parent.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsItemOnlyForOwningUser() {
        TodoItemEntity saved = entityManager.persistAndFlush(item(1L, 10L, null, "Buy milk"));
        entityManager.clear();

        Optional<TodoItemEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<TodoItemEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDescOrdersNewestFirst() throws InterruptedException {
        TodoItemEntity first = entityManager.persistFlushFind(important(1L, 10L, "First important"));
        Thread.sleep(5);
        TodoItemEntity second = entityManager.persistFlushFind(important(1L, 10L, "Second important"));
        entityManager.persistAndFlush(item(1L, 10L, null, "Not important"));
        TodoItemEntity completedImportant = important(1L, 10L, "Completed important");
        completedImportant.setCompleted(true);
        entityManager.persistAndFlush(completedImportant);
        entityManager.clear();

        List<TodoItemEntity> result = repository.findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "most recently created important item must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDescReturnsEmptyWhenNoneMatch() {
        assertTrue(repository.findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDesc(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersItems() {
        TodoItemEntity i1 = entityManager.persistAndFlush(item(1L, 10L, null, "Buy milk"));
        TodoItemEntity i2 = entityManager.persistAndFlush(item(2L, 10L, null, "Other user"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }

    @Test
    void deleteByListIdRemovesOnlyItemsInThatList() {
        TodoItemEntity i1 = entityManager.persistAndFlush(item(1L, 10L, null, "In list 10"));
        TodoItemEntity i2 = entityManager.persistAndFlush(item(1L, 20L, null, "In list 20"));
        entityManager.clear();

        repository.deleteByListId(10L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }

    @Test
    void deleteByParentItemIdRemovesOnlySubItemsOfThatParent() {
        TodoItemEntity parent = entityManager.persistFlushFind(item(1L, 10L, null, "Buy milk"));
        TodoItemEntity subItem = entityManager.persistAndFlush(item(1L, 10L, parent.getId(), "Pick 2%"));
        entityManager.clear();

        repository.deleteByParentItemId(parent.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(subItem.getId()).isEmpty());
        assertTrue(repository.findById(parent.getId()).isPresent());
    }

    @Test
    void countTopLevelItemsByListGroupsCountsByListAndCompletedStatus() {
        TodoItemEntity l10a = entityManager.persistFlushFind(item(1L, 10L, null, "Item A"));
        TodoItemEntity l10b = item(1L, 10L, null, "Item B");
        l10b.setCompleted(true);
        entityManager.persistAndFlush(l10b);
        entityManager.persistAndFlush(item(1L, 10L, l10a.getId(), "Sub-item, not top-level"));
        entityManager.persistAndFlush(item(1L, 20L, null, "Item in list 20"));
        entityManager.persistAndFlush(item(2L, 10L, null, "Different user"));
        entityManager.clear();

        List<TodoItemRepository.TodoListItemCount> counts = repository.countTopLevelItemsByList(1L);

        assertEquals(2, counts.size());
        TodoItemRepository.TodoListItemCount list10Count = counts.stream()
                .filter(c -> c.getListId().equals(10L)).findFirst().orElseThrow();
        TodoItemRepository.TodoListItemCount list20Count = counts.stream()
                .filter(c -> c.getListId().equals(20L)).findFirst().orElseThrow();

        assertEquals(2L, list10Count.getTotal(), "sub-item must be excluded from the top-level count");
        assertEquals(1L, list10Count.getCompletedCount());
        assertEquals(1L, list20Count.getTotal());
        assertEquals(0L, list20Count.getCompletedCount());
    }

    @Test
    void countTopLevelItemsByListReturnsEmptyWhenNoItemsForUser() {
        assertTrue(repository.countTopLevelItemsByList(999L).isEmpty());
    }
}
