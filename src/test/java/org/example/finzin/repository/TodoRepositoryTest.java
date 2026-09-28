package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.TodoEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoRepository repository;

    private TodoEntity todo(Long userId, String title, String description, LocalDate dueDate,
                             String priority, String status, boolean completed) {
        TodoEntity t = new TodoEntity();
        t.setUserId(userId);
        t.setTitle(title);
        t.setDescription(description);
        t.setDueDate(dueDate);
        t.setPriority(priority);
        t.setStatus(status);
        t.setCompleted(completed);
        return t;
    }

    @Test
    void findByStatusOrderByDueDateAscPriorityAscOrdersByDueDateThenPriority() {
        entityManager.persistAndFlush(todo(1L, "Earliest", null, LocalDate.of(2026, 8, 1), "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Same day low", null, LocalDate.of(2026, 8, 5), "low", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Same day high", null, LocalDate.of(2026, 8, 5), "high", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Different status", null, LocalDate.of(2026, 7, 1), "low", "completed", true));
        entityManager.clear();

        List<TodoEntity> result = repository.findByStatusOrderByDueDateAscPriorityAsc("pending");

        assertEquals(3, result.size());
        assertEquals("Earliest", result.get(0).getTitle());
        assertEquals("Same day high", result.get(1).getTitle(), "'high' sorts before 'low' alphabetically");
        assertEquals("Same day low", result.get(2).getTitle());
    }

    @Test
    void findByStatusOrderByDueDateAscPriorityAscReturnsEmptyWhenNoMatch() {
        assertTrue(repository.findByStatusOrderByDueDateAscPriorityAsc("archived").isEmpty());
    }

    @Test
    void searchTodosMatchesTitleOrDescriptionCaseInsensitively() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", "Milk and eggs", null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Call dentist", "book GROCERIES pickup", null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Unrelated", "Nothing here", null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.searchTodos("groceries");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getTitle().equals("Buy Groceries")));
        assertTrue(result.stream().anyMatch(t -> t.getTitle().equals("Call dentist")));
    }

    @Test
    void searchTodosReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", "Milk and eggs", null, "medium", "pending", false));
        entityManager.clear();

        assertTrue(repository.searchTodos("nonexistent").isEmpty());
    }

    @Test
    void findActiveTodosExcludesCompletedStatus() {
        entityManager.persistAndFlush(todo(1L, "Pending one", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "In progress one", null, null, "medium", "in_progress", false));
        entityManager.persistAndFlush(todo(1L, "Completed one", null, null, "medium", "completed", true));
        entityManager.clear();

        List<TodoEntity> result = repository.findActiveTodos();

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(t -> "completed".equals(t.getStatus())));
    }

    @Test
    void findActiveTodosReturnsEmptyWhenAllCompleted() {
        entityManager.persistAndFlush(todo(1L, "Completed one", null, null, "medium", "completed", true));
        entityManager.clear();

        assertTrue(repository.findActiveTodos().isEmpty());
    }

    @Test
    void findTodaysTodosReturnsOnlyActiveTodosDueOnGivenDate() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        entityManager.persistAndFlush(todo(1L, "Due today", null, today, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Due tomorrow", null, today.plusDays(1), "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Completed today", null, today, "medium", "completed", true));
        entityManager.clear();

        List<TodoEntity> result = repository.findTodaysTodos(today);

        assertEquals(1, result.size());
        assertEquals("Due today", result.get(0).getTitle());
    }

    @Test
    void findTodaysTodosReturnsEmptyWhenNoneDueToday() {
        assertTrue(repository.findTodaysTodos(LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void findOverdueTodosReturnsOnlyActiveTodosBeforeGivenDate() {
        LocalDate cutoff = LocalDate.of(2026, 8, 1);
        entityManager.persistAndFlush(todo(1L, "Overdue", null, cutoff.minusDays(1), "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Due on cutoff", null, cutoff, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Overdue but completed", null, cutoff.minusDays(1), "medium", "completed", true));
        entityManager.clear();

        List<TodoEntity> result = repository.findOverdueTodos(cutoff);

        assertEquals(1, result.size());
        assertEquals("Overdue", result.get(0).getTitle());
    }

    @Test
    void findOverdueTodosReturnsEmptyWhenNoneOverdue() {
        assertTrue(repository.findOverdueTodos(LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void countByStatusAndCompletedCountsOnlyMatchingRows() {
        entityManager.persistAndFlush(todo(1L, "A", null, null, "medium", "completed", true));
        entityManager.persistAndFlush(todo(1L, "B", null, null, "medium", "completed", true));
        entityManager.persistAndFlush(todo(1L, "C", null, null, "medium", "completed", false));
        entityManager.persistAndFlush(todo(1L, "D", null, null, "medium", "pending", false));
        entityManager.clear();

        assertEquals(2L, repository.countByStatusAndCompleted("completed", true));
        assertEquals(1L, repository.countByStatusAndCompleted("completed", false));
        assertEquals(0L, repository.countByStatusAndCompleted("pending", true));
    }

    @Test
    void findByUserIdAndStatusFiltersByBothFields() {
        entityManager.persistAndFlush(todo(1L, "Match", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Wrong status", null, null, "medium", "completed", true));
        entityManager.persistAndFlush(todo(2L, "Wrong user", null, null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.findByUserIdAndStatus(1L, "pending");

        assertEquals(1, result.size());
        assertEquals("Match", result.get(0).getTitle());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        assertTrue(repository.findByUserIdAndStatus(1L, "pending").isEmpty());
    }

    @Test
    void findByUserIdAndCompletedFalseExcludesCompletedTodosForThatUser() {
        entityManager.persistAndFlush(todo(1L, "Not done", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Done", null, null, "medium", "completed", true));
        entityManager.persistAndFlush(todo(2L, "Other user", null, null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.findByUserIdAndCompletedFalse(1L);

        assertEquals(1, result.size());
        assertEquals("Not done", result.get(0).getTitle());
    }

    @Test
    void findByUserIdAndCompletedFalseReturnsEmptyWhenAllCompleted() {
        entityManager.persistAndFlush(todo(1L, "Done", null, null, "medium", "completed", true));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndCompletedFalse(1L).isEmpty());
    }

    @Test
    void searchByUserIdAndTitleMatchesOnlyWithinThatUsersTodos() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(2L, "Buy Groceries too", null, null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.searchByUserIdAndTitle(1L, "groceries");

        assertEquals(1, result.size());
        assertEquals("Buy Groceries", result.get(0).getTitle());
    }

    @Test
    void searchByUserIdAndTitleReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", null, null, "medium", "pending", false));
        entityManager.clear();

        assertTrue(repository.searchByUserIdAndTitle(1L, "nonexistent").isEmpty());
    }

    @Test
    void searchByUserIdMatchesOnlyWithinThatUsersTodos() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(2L, "Buy Groceries too", null, null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.searchByUserId(1L, "groceries");

        assertEquals(1, result.size());
        assertEquals("Buy Groceries", result.get(0).getTitle());
    }

    @Test
    void searchByUserIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(todo(1L, "Buy Groceries", null, null, "medium", "pending", false));
        entityManager.clear();

        assertTrue(repository.searchByUserId(1L, "nonexistent").isEmpty());
    }

    @Test
    void findActiveTodosByUserIdExcludesCompletedAndOtherUsers() {
        entityManager.persistAndFlush(todo(1L, "Active", null, null, "medium", "pending", false));
        entityManager.persistAndFlush(todo(1L, "Completed", null, null, "medium", "completed", true));
        entityManager.persistAndFlush(todo(2L, "Other user active", null, null, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.findActiveTodosByUserId(1L);

        assertEquals(1, result.size());
        assertEquals("Active", result.get(0).getTitle());
    }

    @Test
    void findActiveTodosByUserIdReturnsEmptyWhenNoneActive() {
        assertTrue(repository.findActiveTodosByUserId(1L).isEmpty());
    }

    @Test
    void findTodaysTodosByUserIdReturnsOnlyThatUsersTodosDueToday() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        entityManager.persistAndFlush(todo(1L, "Due today", null, today, "medium", "pending", false));
        entityManager.persistAndFlush(todo(2L, "Other user due today", null, today, "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.findTodaysTodosByUserId(1L, today);

        assertEquals(1, result.size());
        assertEquals("Due today", result.get(0).getTitle());
    }

    @Test
    void findTodaysTodosByUserIdReturnsEmptyWhenNoneDueToday() {
        assertTrue(repository.findTodaysTodosByUserId(1L, LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void findOverdueTodosByUserIdReturnsOnlyThatUsersOverdueTodos() {
        LocalDate cutoff = LocalDate.of(2026, 8, 1);
        entityManager.persistAndFlush(todo(1L, "Overdue", null, cutoff.minusDays(1), "medium", "pending", false));
        entityManager.persistAndFlush(todo(2L, "Other user overdue", null, cutoff.minusDays(1), "medium", "pending", false));
        entityManager.clear();

        List<TodoEntity> result = repository.findOverdueTodosByUserId(1L, cutoff);

        assertEquals(1, result.size());
        assertEquals("Overdue", result.get(0).getTitle());
    }

    @Test
    void findOverdueTodosByUserIdReturnsEmptyWhenNoneOverdue() {
        assertTrue(repository.findOverdueTodosByUserId(1L, LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersTodos() {
        TodoEntity t1 = entityManager.persistAndFlush(todo(1L, "Mine", null, null, "medium", "pending", false));
        TodoEntity t2 = entityManager.persistAndFlush(todo(2L, "Other", null, null, "medium", "pending", false));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(t1.getId()).isEmpty());
        assertTrue(repository.findById(t2.getId()).isPresent());
    }
}
